package edu.stanford.nlp.mt.decoder;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.StateLatticeDecoder;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DTURule;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValues;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;

/**
 * Abstract interfaces and algorithms that apply to all inference algorithms.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInferer<TK, FV> extends
    AbstractInferer<TK, FV> {

  private static final boolean DEBUG = false;
  
  public final int beamCapacity;
  public final BeamFactory.BeamType beamType;
  private final Comparator<RichTranslation<TK,FV>> translationComparator;
  
  /**
   * Constructor.
   * 
   * @param builder
   */
  protected AbstractBeamInferer(AbstractBeamInfererBuilder<TK, FV> builder) {
    super(builder);
    this.beamCapacity = builder.beamCapacity;
    this.beamType = builder.beamType;
    this.translationComparator = new RichTranslationComparator<TK,FV>();
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size, boolean distinct) {
    return nbest(scorer, source, sourceInputId, sourceInputProperties,
        outputSpace, targets, size, distinct);
  }

  /**
   * Remove source words that cannot be covered by a span in the phrase table.
   * 
   * @param source
   * @return
   */
  private Sequence<TK> filterUnknownWords(Sequence<TK> source) {
    if (source == null) return null;
    List<ConcreteRule<TK,FV>> rules = phraseGenerator.getRules(source, null, null, -1, null);

    CoverageSet possibleCoverage = new CoverageSet();
    for (ConcreteRule<TK,FV> rule : rules) {
      if (rule.abstractRule.target.size() > 0) {
        possibleCoverage.or(rule.sourceCoverage);
      }
    }

    List<TK> filteredToks = Generics.newLinkedList();
    for (int i = 0; i  < source.size(); i++) {
      if (possibleCoverage.get(i)) {
        filteredToks.add(source.get(i));
      }
    }
    return filteredToks.size() > 0 ? new SimpleSequence<TK>(filteredToks) : null;
  }

  /**
   * TODO(spenceg): This routine is *extremely* inefficient. Rewrite with e.g., Huang and Chiang's
   * k-best extraction or something.
   */
  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size, boolean distinct) {

    // filter unknown words
    if (filterUnknownWords) {
       source = filterUnknownWords(source);
    }
    if (source == null) return null;
    if (outputSpace != null) outputSpace.setSourceSequence(source);
    
    // Decoding
    RecombinationHistory<Derivation<TK, FV>> recombinationHistory = 
        new RecombinationHistory<Derivation<TK, FV>>();
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        recombinationHistory, outputSpace, targets, size);
    if (beam == null) {
      // Decoder failure
      return null;
    }
    List<Derivation<TK, FV>> goalStates = Generics.newArrayList(beam.size());
    for (Derivation<TK, FV> hyp : beam) {
      goalStates.add(hyp);
    }

    // Setup for n-best extraction
    StateLatticeDecoder<Derivation<TK, FV>> latticeDecoder = new StateLatticeDecoder<Derivation<TK, FV>>(
        goalStates, recombinationHistory);
    Set<Sequence<TK>> distinctSurfaceTranslations = Generics.newHashSet();

    // Extract
    List<RichTranslation<TK, FV>> translations = Generics.newLinkedList();
    final long nbestStartTime = System.nanoTime();
    
    // Limit the number of popped items in the case of distinct nbest lists.
    // We want the algorithm to terminate eventually....
    final int maxItemsToExtract = distinct ? size * 5 : Integer.MAX_VALUE;
    int numExtracted = 0;
    long nbestId = 0;
    for (List<Derivation<TK, FV>> latticePath : latticeDecoder) {
      if (numExtracted >= maxItemsToExtract) {
        break;
      }
      // DTU stuff
      boolean withDTUs = false;
      Set<Rule<TK>> seenOptions = Generics.newHashSet();
      
      // TODO(spenceg): This is very inefficient. Reconstruct the derivation
      // from the lattice path since the current n-best list extractor
      // does not set the parent references when it traverses the lattice. These
      // references may be incorrect due to recombination.
      // When we replace StateLatticeDecoder, this code should go away.
      Derivation<TK, FV> goalHyp = null;
      for (Derivation<TK, FV> node : latticePath) {
        if (goalHyp == null) {
          goalHyp = node;
          continue;
        }
        if (node.rule.abstractRule instanceof DTURule)
          withDTUs = true;
        if (withDTUs) {
          goalHyp = new DTUHypothesis<TK, FV>(sourceInputId,
              node.rule, goalHyp.length, goalHyp, node, featurizer,
              scorer, heuristic, seenOptions);
        } else {
          goalHyp = new Derivation<TK, FV>(sourceInputId, node.rule,
              goalHyp.length, goalHyp, featurizer, scorer, heuristic);
        }
      }

      if (withDTUs) {
        DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) goalHyp;
        if (!dtuHyp.isDone() || dtuHyp.hasExpired())
          System.err.printf("Warning: option not complete(%d,%s): %s\n",
              translations.size(), dtuHyp.hasExpired(), goalHyp);
      }

      // Decoder failure in which the null hypothesis was returned.
      if (goalHyp == null || goalHyp.featurizable == null) {
        System.err.printf("%s: WARNING: null hypothesis encountered for input %d; decoder failed%n", 
            this.getClass().getName(), sourceInputId);
        return null;
      }
      
      ++numExtracted;
      
      if (distinct) {
        if (distinctSurfaceTranslations.contains(goalHyp.featurizable.targetPrefix)) {
          // Seen a higher-scoring derivation with this target string before
          continue;
        } else {
          distinctSurfaceTranslations.add(goalHyp.featurizable.targetPrefix);
        }
      }
      
      translations.add(new RichTranslation<TK, FV>(goalHyp,
            goalHyp.score, FeatureValues.combine(goalHyp), nbestId++));
      if (translations.size() >= size) {
        break;
      }
    }

    // If a non-admissible recombination heuristic is used, the hypothesis
    // scores predicted by the lattice may not actually correspond to their real
    // scores.
    // Since the n-best list should be sorted according to the true scores, we
    // re-sort things here just in case.
    Collections.sort(translations, translationComparator);

    if (DEBUG) {
      System.err.printf("source id %d: #extracted: %d #final: %d time: %.3fsec%n", sourceInputId, numExtracted, translations.size(),
          (System.nanoTime() - nbestStartTime) / 1e9);
    }
    
    return translations;
  }
  
  private static class RichTranslationComparator<TK,FV> implements Comparator<RichTranslation<TK,FV>> {
    @Override
    public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
      return (int) Math.signum(o2.score - o1.score);
    }
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> constrainedOutputSpace, List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, sourceInputProperties,
        constrainedOutputSpace, targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {
    
    // filter unknown source words
    if (filterUnknownWords) {
      source = filterUnknownWords(source);
    }
    if (source == null) return null;
    if (outputSpace != null) outputSpace.setSourceSequence(source);
    
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        null, outputSpace, targets, 1);
    if (beam == null)
      return null;
    Derivation<TK, FV> hyp = beam.iterator().next();
    return new RichTranslation<TK, FV>(hyp, hyp.score,
        FeatureValues.combine(hyp), 0);
  }

  /**
	 * 
	 */
  abstract protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int nbest);

  /**
   *
   */
  @SuppressWarnings("unchecked")
  protected Beam<Derivation<TK, FV>>[] createBeamsForCoverageCounts(
      int beamCnt, int capacity,
      RecombinationFilter<Derivation<TK, FV>> filter,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {
    Beam<Derivation<TK, FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = BeamFactory.factory(beamType, filter, capacity,
          recombinationHistory);
    }
    return beams;
  }

  /**
   *
   */
  protected Beam<Derivation<TK, FV>>[] createBeamsForCoverageCounts(
      int beamCnt, int capacity, RecombinationFilter<Derivation<TK, FV>> filter) {
    @SuppressWarnings("unchecked")
    Beam<Derivation<TK, FV>>[] beams = new Beam[beamCnt];
    for (int i = 0; i < beams.length; i++) {
      beams[i] = BeamFactory.factory(beamType, filter, capacity);
    }
    return beams;
  }

  /**
   * 
   * @author danielcer
   */
  public class CoverageBeams {
    final private Map<CoverageSet, Beam<Derivation<TK, FV>>> beams = Generics.newHashMap();
    final private Set<CoverageSet>[] coverageCountToCoverageSets;
    final private RecombinationHistory<Derivation<TK, FV>> recombinationHistory;

    @SuppressWarnings("unchecked")
    public CoverageBeams(int sourceSize,
        RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {
      coverageCountToCoverageSets = new Set[sourceSize + 1];
      for (int i = 0; i < sourceSize + 1; i++) {
        coverageCountToCoverageSets[i] = Generics.newHashSet();
      }
      this.recombinationHistory = recombinationHistory;
    }

    public void put(Derivation<TK, FV> hypothesis) {
      get(hypothesis.sourceCoverage).put(hypothesis);
    }

    private Beam<Derivation<TK, FV>> get(CoverageSet coverage) {
      Beam<Derivation<TK, FV>> beam = beams.get(coverage);
      if (beam == null) {
        beam = BeamFactory.factory(beamType, filter, beamCapacity,
            recombinationHistory);
        beams.put(coverage, beam);
        int coverageCount = coverage.cardinality();
        coverageCountToCoverageSets[coverageCount].add(coverage);
      }
      return beam;
    }

    public List<Derivation<TK, FV>> getHypotheses(int coverageCount) {
      List<Derivation<TK, FV>> hypothesisList = Generics.newLinkedList();

      for (CoverageSet coverage : coverageCountToCoverageSets[coverageCount]) {
        Beam<Derivation<TK, FV>> hypothesisBeam = get(coverage);
        for (Derivation<TK, FV> hypothesis : hypothesisBeam) {
          hypothesisList.add(hypothesis);
        }
      }

      return hypothesisList;
    }
  }

  abstract public void dump(Derivation<TK, FV> hyp);
}
