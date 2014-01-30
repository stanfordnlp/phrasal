package edu.stanford.nlp.mt.decoder;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.mt.base.AbstractSequence;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.DTURule;
import edu.stanford.nlp.mt.base.FeatureValues;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.StateLatticeDecoder;
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

  static public final String DEBUG_OPT = "AbstractBeamInfererDebug";
  static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_OPT, "false"));
  static public boolean DISTINCT_SURFACE_TRANSLATIONS = false;
  public final int beamCapacity;
  public final BeamFactory.BeamType beamType;

  public static final int MAX_DUPLICATE_FACTOR = 10;
  public static final int SAFE_LIST = 500;

  protected AbstractBeamInferer(AbstractBeamInfererBuilder<TK, FV> builder) {
    super(builder);
    this.beamCapacity = builder.beamCapacity;
    this.beamType = builder.beamType;
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int size) {
    return nbest(scorer, source, sourceInputId, outputSpace,
        targets, size);
  }

  /**
   * Remove source words that cannot be covered by a span in the phrase table.
   * 
   * @param source
   * @return
   */
  private Sequence<TK> filterUnknownWords(Sequence<TK> source) {
    if (source == null) return null;
    List<ConcreteRule<TK,FV>> rules = phraseGenerator.getRules(source, null, -1, null);

    CoverageSet possibleCoverage = new CoverageSet();
    for (ConcreteRule<TK,FV> rule : rules) {
      if (rule.abstractRule.target.size() > 0 && !"".equals(rule.abstractRule.target.toString())) {
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

  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int size) {

    // filter unknown words
    if (filterUnknownWords) {
       source = filterUnknownWords(source);
       if (outputSpace != null) outputSpace.setSourceSequence(source);
    }
    if (source == null) return null;
    
    // Decoding
    RecombinationHistory<Derivation<TK, FV>> recombinationHistory = 
        new RecombinationHistory<Derivation<TK, FV>>();
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId,
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
    if (DISTINCT_SURFACE_TRANSLATIONS) {
      System.err.println("N-best list with distinct surface strings: " + DISTINCT_SURFACE_TRANSLATIONS);
    }

    // Extract
    int hypCount = 0;
    final int maxDuplicateCount = size * MAX_DUPLICATE_FACTOR;
    List<RichTranslation<TK, FV>> translations = Generics.newLinkedList();
    final long nbestStartTime = System.nanoTime();
    for (List<Derivation<TK, FV>> hypList : latticeDecoder) {
      boolean withDTUs = false;
      ++hypCount;
      Derivation<TK, FV> hyp = null;
      Set<Rule<TK>> seenOptions = Generics.newHashSet();

      for (Derivation<TK, FV> nextHyp : hypList) {
        if (hyp == null) {
          hyp = nextHyp;
          continue;
        }
        if (nextHyp.rule.abstractRule instanceof DTURule)
          withDTUs = true;
        if (withDTUs) {
          hyp = new DTUHypothesis<TK, FV>(sourceInputId,
              nextHyp.rule, hyp.length, hyp, nextHyp, featurizer,
              scorer, heuristic, seenOptions);
        } else {
          hyp = new Derivation<TK, FV>(sourceInputId, nextHyp.rule,
              hyp.length, hyp, featurizer, scorer, heuristic);
        }
      }

      if (withDTUs) {
        DTUHypothesis<TK, FV> dtuHyp = (DTUHypothesis<TK, FV>) hyp;
        if (!dtuHyp.isDone() || dtuHyp.hasExpired())
          System.err.printf("Warning: option not complete(%d,%s): %s\n",
              translations.size(), dtuHyp.hasExpired(), hyp);
      }

      // Decoder failure in which the null hypothesis was returned.
      if (hyp == null || hyp.featurizable == null) {
        System.err.printf("%s: WARNING: null hypothesis encountered for input %d; decoder failed%n", 
            this.getClass().getName(), sourceInputId);
        return null;
      }
      
      if (DISTINCT_SURFACE_TRANSLATIONS) {
        // Get surface string:
        AbstractSequence<TK> seq = (AbstractSequence<TK>) hyp.featurizable.targetPrefix;

        // If seen this string before and not among the top-k, skip it:
        if (hypCount > SAFE_LIST && distinctSurfaceTranslations.contains(seq)) {
          continue;
        }

        // Add current hypothesis to nbest list and set of uniq strings:
        Derivation<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable,
            hyp.score, FeatureValues.combine(hyp), beamGoalHyp.id));
        distinctSurfaceTranslations.add(seq);
        if (distinctSurfaceTranslations.size() >= size
            || hypCount >= maxDuplicateCount)
          break;

      } else {
        Derivation<TK, FV> beamGoalHyp = hypList.get(hypList.size() - 1);
        translations.add(new RichTranslation<TK, FV>(hyp.featurizable,
            hyp.score, FeatureValues.combine(hyp), beamGoalHyp.id));
        if (translations.size() >= size) {
          break;
        }
      }
    }

    // If a non-admissible recombination heuristic is used, the hypothesis
    // scores predicted by the lattice may not actually correspond to their real
    // scores.
    // Since the n-best list should be sorted according to the true scores, we
    // re-sort things here just in case.
    Collections.sort(translations, new Comparator<RichTranslation<TK, FV>>() {
      @Override
      public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
        return (int) Math.signum(o2.score - o1.score);
      }
    });

    assert (!translations.isEmpty());
    System.err.printf("source id %d: n-best list size: %d%n", sourceInputId, translations.size());
    if (DEBUG) {
      long nBestConstructionTime = System.nanoTime() - nbestStartTime;
      System.err.printf("N-best generation time: %.3f seconds\n",
          nBestConstructionTime / 1e9);
    }

    if (DISTINCT_SURFACE_TRANSLATIONS) {
      List<RichTranslation<TK, FV>> dtranslations = Generics.newLinkedList();
      distinctSurfaceTranslations.clear();
      for (RichTranslation<TK, FV> rt : translations) {
        if (distinctSurfaceTranslations.contains(rt.translation)) {
          continue;
        }
        distinctSurfaceTranslations.add(rt.translation);
        dtranslations.add(rt);
      }
      return dtranslations;
    } else {
      return translations;
    }
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, OutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, constrainedOutputSpace,
        targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets) {
    
    // filter unknown source words
    if (filterUnknownWords) {
      source = filterUnknownWords(source);
      if (outputSpace != null) outputSpace.setSourceSequence(source);
    }
    if (source == null) return null;
    
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId,
        null, outputSpace, targets, 1);
    if (beam == null)
      return null;
    Derivation<TK, FV> hyp = beam.iterator().next();
    return new RichTranslation<TK, FV>(hyp.featurizable, hyp.score,
        FeatureValues.combine(hyp), 0);
  }

  /**
	 * 
	 */
  abstract protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
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
