package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.StateLatticeDecoder;
import edu.stanford.nlp.mt.decoder.util.PrefixRuleGrid;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DTURule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.FeatureValues;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * An abstract interface for beam-based inference algorithms.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInferer<TK, FV> extends
    AbstractInferer<TK, FV> {

  private static final Logger logger = LogManager.getLogger(AbstractBeamInferer.class.getName());
  
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
    this.beamCapacity = builder.beamSize;
    this.beamType = builder.beamType;
    this.translationComparator = new Comparator<RichTranslation<TK,FV>>() {
      @Override
      public int compare(RichTranslation<TK, FV> o1, RichTranslation<TK, FV> o2) {
        return (int) Math.signum(o2.score - o1.score);
      }
    };
  }

  @Override
  public List<RichTranslation<TK, FV>> nbest(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets, int size, boolean distinct) {
    return nbest(scorer, source, sourceInputId, sourceInputProperties,
        outputSpace, targets, size, distinct);
  }

  // TODO(spenceg) Relax this constraint once we consolidate LM scores
  private static final int MAX_HYPS_PER_BEAM = 100;
  
  /**
   * Populate the beams given the prefix. Returns 0 if the prefix is of length 0.
   * 
   * @param source
   * @param ruleList
   * @param sourceInputProperties
   * @param prefix
   * @param scorer
   * @param beams
   * @return The beam at which standard decoding should begin.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected int prefixFillBeams(Sequence<TK> source, List<ConcreteRule<TK,FV>> ruleList,
      InputProperties sourceInputProperties, Sequence<TK> prefix, Scorer<FV> scorer, 
      List<Beam<Derivation<TK,FV>>> beams, int sourceInputId, OutputSpace<TK, FV> outputSpace) {
    if (source == null || source.size() == 0 || prefix == null || prefix.size() == 0) return 0;
    
    // Sort rule list by target
    PrefixRuleGrid<TK,FV> ruleGrid = new PrefixRuleGrid<TK,FV>(ruleList, source, prefix);
    
    // Augment grid (if necessary)
    if (phraseGenerator instanceof DynamicTranslationModel) {
      DynamicTranslationModel<FV> foregroundTM = null;
      if (sourceInputProperties.containsKey(InputProperty.ForegroundTM)) {
        foregroundTM = 
            (DynamicTranslationModel) sourceInputProperties.get(InputProperty.ForegroundTM);
      }
      ruleGrid.augmentGrid(((DynamicTranslationModel<FV>) phraseGenerator), foregroundTM,
          scorer, featurizer, sourceInputProperties, sourceInputId);
    }

    // Populate beams (indexed by source coverage)
    // If this is cube pruning, then there is no need to create hyperedge bundles
    // because the LM score is constant. We can simply sort the derivations.
    // TODO(spenceg) Only compute LM scores once.
    int[] prefixCoverages = IntStream.range(0, prefix.size() + phraseGenerator.maxLengthTarget())
        .map(i -> Integer.MAX_VALUE).toArray();
    int maxPrefix = 0;
    int[] hypsForBeam = new int[beams.size()];
    int numHyps = 0;
    for (int i = 0, sz = beams.size(); i < sz; ++i) {
      final int beamCardinality = i;
      for (Derivation<TK,FV> antecedent : beams.get(beamCardinality)) {
        int insertionPosition = antecedent.targetSequence.size();
        if (insertionPosition >= prefix.size()) {
//          System.err.printf("OVERLAP %d %d: %f %s %s%n", i, hypsForBeam, antecedent.score, 
//              antecedent.sourceCoverage, antecedent.targetSequence.toString());
          continue;
        }
        List<ConcreteRule<TK,FV>> rulesForPosition = ruleGrid.get(insertionPosition);
        for (ConcreteRule<TK,FV> rule : rulesForPosition) {
          if (antecedent.sourceCoverage.intersects(rule.sourceCoverage)) continue; // Check source coverage
          CoverageSet testCoverage = antecedent.sourceCoverage.clone();
          testCoverage.or(rule.sourceCoverage);
          int succCardinality = testCoverage.cardinality();
          boolean capacityExceeded = hypsForBeam[succCardinality] > MAX_HYPS_PER_BEAM;
          if (capacityExceeded) continue; // Check beam capacity
          Derivation<TK,FV> successor = new Derivation<>(sourceInputId, rule, insertionPosition, antecedent, featurizer,
              scorer, heuristic, outputSpace);
          assert succCardinality == successor.sourceCoverage.cardinality();
          ++numHyps;
          hypsForBeam[succCardinality]++;
          // WSGDEBUG
//          System.err.printf("%d %d: %f %s %s%n", i, hypsForBeam, successor.score, 
//              successor.sourceCoverage, successor.targetSequence.toString());
          beams.get(succCardinality).put(successor);
          
          // Book-keeping
          maxPrefix = Math.max(maxPrefix, successor.targetSequence.size());
          if (succCardinality < prefixCoverages[successor.targetSequence.size()]) {
            prefixCoverages[successor.targetSequence.size()] = succCardinality;
          }
        }
      }
    }
    logger.info("Input {}: {} prefix hypotheses generated", sourceInputId, numHyps);
    
    // WSGDEBUG
//    for (int i = 0; i < beams.size(); ++i) {
//      System.err.printf("BEAM %d%n", i);
//      BundleBeam<TK,FV> beam = (BundleBeam<TK, FV>) beams.get(i);
//      System.err.println(beam.beamString());
//      System.err.println("================");
//    }
    
    // Clamp the maxPrefix to the prefix length
    maxPrefix = Math.min(maxPrefix, prefix.size());
    if (prefixCoverages[maxPrefix] == Integer.MAX_VALUE) {
      logger.warn("input {}: No prefix coverage.", sourceInputId);
      return 0;
    }
    
    // Return beam number of the starting point (longest prefix with the minimum source
    // coverage)
    return maxPrefix >= 0 ? prefixCoverages[maxPrefix] : 0;
  }
  
  /**
   * Query the phrase table and decide how to handle unknown words.
   * 
   * @param source
   * @param sourceInputProperties
   * @param targets
   * @param sourceInputId
   * @param scorer
   * @return
   */
  protected PhraseQuery<TK,FV> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // Initial query
    List<ConcreteRule<TK,FV>> ruleList = phraseGenerator
        .getRules(source, sourceInputProperties, sourceInputId, scorer);
    
    // Compute coverage
    CoverageSet coverage = new CoverageSet(source.size());
    for (ConcreteRule<TK,FV> rule : ruleList) {
      coverage.or(rule.sourceCoverage);
    }
    
    // Decide what to do if the coverage set is incomplete
    if (coverage.cardinality() != source.size()) {
      if (filterUnknownWords) {
        // Filter OOVs from the source and then query the phrase table again
        List<TK> filteredToks = new ArrayList<>(source.size());
        for (int i = 0, sz = source.size(); i  < sz; i++) {
          if (coverage.get(i)) {
            filteredToks.add(source.get(i));
          }
        }
        Sequence<TK> sourceFiltered = filteredToks.size() > 0 ? 
            new ArraySequence<TK>(filteredToks) : Sequences.emptySequence();
        ruleList = phraseGenerator.getRules(sourceFiltered, sourceInputProperties, sourceInputId, scorer);
        return new PhraseQuery<>(sourceFiltered, ruleList);
        
      } else {
        // Add rules from the OOV model
        for (int i = 0, sz = source.size(); i  < sz; i++) {
          if (coverage.get(i)) {
            continue;
          }
          int gapIndex = i;
          Sequence<TK> queryWord = source.subsequence(gapIndex, gapIndex + 1);
          List<ConcreteRule<TK,FV>> oovRules = 
              unknownWordModel.getRules(queryWord, sourceInputProperties, sourceInputId, scorer);
          CoverageSet oovCoverage = new CoverageSet(source.size());
          oovCoverage.set(gapIndex);
          for (ConcreteRule<TK,FV> rule : oovRules) {
            // Update the coverage set for the output of the OOV model
            ruleList.add(new ConcreteRule<TK,FV>(rule.abstractRule, 
                oovCoverage, featurizer, scorer, source, sourceInputId, 
                sourceInputProperties));
          }
        }
      }
    }
    return new PhraseQuery<>(source, ruleList);
  }
  
  /**
   * Container for the result of a TM query.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
  public static class PhraseQuery<TK,FV> {
    public final List<ConcreteRule<TK,FV>> ruleList;
    public final Sequence<TK> filteredSource;
    public PhraseQuery(Sequence<TK> filteredSource, List<ConcreteRule<TK,FV>> ruleList) {
      this.ruleList = ruleList;
      this.filteredSource = filteredSource;
    }
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

    if (outputSpace != null) outputSpace.setSourceSequence(source);
    
    TimeKeeper timer = TimingUtils.start();
    
    // Decoding
    RecombinationHistory<Derivation<TK, FV>> recombinationHistory = new RecombinationHistory<>();
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        recombinationHistory, outputSpace, targets, size);
    timer.mark("Decode");
    
    if (beam == null) {
      // Decoder failure
      return null;
    }
    List<Derivation<TK, FV>> goalStates = new ArrayList<>(beam.size());
    for (Derivation<TK, FV> hyp : beam) {
      goalStates.add(hyp);
    }

    // Setup for n-best extraction
    StateLatticeDecoder<Derivation<TK, FV>> latticeDecoder = new StateLatticeDecoder<Derivation<TK, FV>>(
        goalStates, recombinationHistory);
    Set<Sequence<TK>> distinctSurfaceTranslations = new HashSet<>();

    // Extract
    List<RichTranslation<TK, FV>> translations = new ArrayList<>(size);
    
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
      Set<Rule<TK>> seenOptions = new HashSet<>();
      
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
              scorer, heuristic, seenOptions, outputSpace);
        } else {
          goalHyp = new Derivation<TK, FV>(sourceInputId, node.rule,
              goalHyp.length, goalHyp, featurizer, scorer, heuristic, outputSpace);
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
        logger.warn("Input {}: null hypothesis encountered. Decoder failed", sourceInputId);
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
      
      translations.add(new RichTranslation<TK, FV>(goalHyp.featurizable,
            goalHyp.score, FeatureValues.combine(goalHyp), nbestId++));
      if (translations.size() >= size) {
        break;
      }
    }
    timer.mark("Extraction");

    // If a non-admissible recombination heuristic is used, the hypothesis
    // scores predicted by the lattice may not actually correspond to their real
    // scores.
    // Since the n-best list should be sorted according to the true scores, we
    // re-sort things here just in case.
    Collections.sort(translations, translationComparator);
    timer.mark("Sort");

    logger.info("Input {}: nbest #extracted {}", sourceInputId, numExtracted);
    logger.info("Input {}: nbest timing {}", sourceInputId, timer);
    
    return translations;
  }

  @Override
  public RichTranslation<TK, FV> translate(Sequence<TK> source,
      int sourceInputId, InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, sourceInputProperties, outputSpace, targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {

    if (outputSpace != null) outputSpace.setSourceSequence(source);
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
        null, outputSpace, targets, 1);
    if (beam == null) return null;
    final Derivation<TK, FV> hyp = beam.iterator().next();
    return new RichTranslation<TK, FV>(hyp.featurizable, hyp.score, FeatureValues.combine(hyp), 0);
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

  abstract public void dump(Derivation<TK, FV> hyp);
}
