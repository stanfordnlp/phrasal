package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

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
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.util.Pair;

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
   * Completion container. Holds a completion sequence and its score.
   */
  private static class Completion<TK> {
    public Sequence<TK> completion;
    public double score;
    public Completion(Sequence<TK> seq, double scr) {
      completion = seq;
      score = scr;
    }
    public String toString() {
      return String.format("%s [%.6f]", completion, score);
    }
  }
  private static class CompletionComparator<TK> implements Comparator<Completion<TK>> {
    public int compare(Completion<TK> x, Completion<TK> y) {
      return (int) Math.signum(y.score - x.score);
    }
  }

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
      Iterable<Derivation<TK,FV>> iterable = () -> beams.get(beamCardinality).iterator();
      // yield from iterator is sorted
      List<Derivation<TK,FV>> sortedAntecedents = StreamSupport.stream(iterable.spliterator(), false)
          .collect(Collectors.toList());
      for (Derivation<TK,FV> antecedent : sortedAntecedents) {
        int insertionPosition = antecedent.targetSequence.size();
        if (insertionPosition >= prefix.size()) {
//          System.err.printf("OVERLAP %d %d: %f %s %s%n", i, hypsForBeam, antecedent.score, 
//              antecedent.sourceCoverage, antecedent.targetSequence.toString());
          continue;
        }
        List<ConcreteRule<TK,FV>> rulesForPosition = ruleGrid.get(insertionPosition);
        for (ConcreteRule<TK,FV> rule : rulesForPosition) {
          if (antecedent.sourceCoverage.intersects(rule.sourceCoverage)) continue; // Check source coverage
          CoverageSet testCoverage = new CoverageSet();
          testCoverage.or(antecedent.sourceCoverage);
          testCoverage.or(rule.sourceCoverage);
          int succCardinality = testCoverage.cardinality();
          boolean capacityExceeded = hypsForBeam[succCardinality] >= beams.get(succCardinality).capacity();
          if (capacityExceeded) break;
          Derivation<TK,FV> successor = new Derivation<>(sourceInputId, rule, 0, antecedent, featurizer,
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
  protected Pair<Sequence<TK>,List<ConcreteRule<TK,FV>>> getRules(Sequence<TK> source,
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
        List<TK> filteredToks = new LinkedList<>();
        for (int i = 0, sz = source.size(); i  < sz; i++) {
          if (coverage.get(i)) {
            filteredToks.add(source.get(i));
          }
        }
        Sequence<TK> sourceFiltered = filteredToks.size() > 0 ? 
            new SimpleSequence<TK>(filteredToks) : Sequences.emptySequence();
        ruleList = phraseGenerator.getRules(sourceFiltered, sourceInputProperties, sourceInputId, scorer);
        return new Pair<>(sourceFiltered, ruleList);
        
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
    return new Pair<>(source, ruleList);
  }
  
  /**
   * TODO(spenceg): This routine is *extremely* inefficient. Rewrite with e.g., Huang and Chiang's
   * k-best extraction or something.
   */
  //@Override
  public List<RichTranslation<TK, FV>> nbestLegacy(Scorer<FV> scorer,
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

  /**
   * Faster and more diverse n-best extraction directly from beam given a target prefix.
   */
  @Override
  public List<RichTranslation<TK, FV>> nbest(Scorer<FV> scorer,
                                             Sequence<TK> source, int sourceInputId,
                                             InputProperties sourceInputProperties,
                                             OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets,
                                             int size, boolean distinct) {

    if (outputSpace != null) outputSpace.setSourceSequence(source);

    // Decoding part, identical to nbestLegacy() -> TODO(sasa): move to common function
    RecombinationHistory<Derivation<TK, FV>> recombinationHistory =
            new RecombinationHistory<Derivation<TK, FV>>();
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
            recombinationHistory, outputSpace, targets, size);
    if (beam == null) {
      // Decoder failure
      return null;
    }
    List<Derivation<TK, FV>> goalStates = new ArrayList<>(beam.size());
    for (Derivation<TK, FV> hyp : beam) {
      goalStates.add(hyp);
    }

    List<RichTranslation<TK, FV>> translations = new LinkedList<>();
    final long nbestStartTime = System.nanoTime();

    // Check if FA prefix is set
    final int prefixLength = outputSpace.getPrefixLength();

    long nbestId = 0;
    HashMap<Sequence<TK>, Double> localCompletions = new HashMap<>();
    HashMap<Sequence<TK>, Double> completions = new HashMap<>();
    PriorityQueue<RichTranslation<TK, FV>> transPQ = new PriorityQueue<>(size, new RichTranslationComparator<TK, FV>());
    PriorityQueue<Completion<TK>> complPQ = new PriorityQueue<>(size, new CompletionComparator<TK>());

    /*
     * Locate target prefix:
     * - iterate back through predecessor derivations for all goal states
     * - stop when target sequence length equals prefix length
     * - add full goal state sequence to translation PQ
     * - add local completion sequence to completion PQ, with score: (goal state - prefix end state)
     */
    for (Derivation<TK, FV> gs : goalStates) {
      logger.debug(String.format("src='%s', trg='%s', score=%.6f, gs=%d",
              gs.sourceSequence, gs.targetSequence, gs.score, gs.id));
      Derivation<TK, FV> parent = gs.preceedingDerivation;
      if ((completions.containsKey(gs.targetSequence) && completions.get(gs.targetSequence) < gs.score) ||
              !completions.containsKey(gs.targetSequence)) {
        completions.put(gs.targetSequence, gs.score);
        transPQ.add(new RichTranslation<TK, FV>(gs.featurizable, gs.score, FeatureValues.combine(gs), nbestId++));
      }
      while (parent != null && (parent.targetSequence.size() > prefixLength)) {
        // find derivation where completion starts
        parent = parent.preceedingDerivation;
      }
      // extract completion (target sequence suffix)
      Sequence<TK> compl = gs.targetSequence.subsequence(prefixLength, gs.targetSequence.size());
      double complScore = parent == null ? gs.score : gs.score - parent.score;
      if ((localCompletions.containsKey(compl) &&
              localCompletions.get(compl) < complScore) ||
              !localCompletions.containsKey(compl)) {
        // store best local completions
        localCompletions.put(compl, complScore);
        complPQ.add(new Completion<>(compl, complScore));
      }
    }

    List<RichTranslation<TK, FV>> finalTranslations = new LinkedList<>();
    Set<TK> seenCompl = new HashSet<>();
    int nExtracted = 0;
    while (!transPQ.isEmpty()) {
      RichTranslation<TK, FV> trans = transPQ.poll();
      // we store the first word of the completion for diversity constraint reasons (see below)
      TK complWord = prefixLength < trans.translation.size() ? trans.translation.get(prefixLength)
              : trans.translation.get(trans.translation.size()-1);
      if (nExtracted >= size/2 && !seenCompl.contains(complWord)) {
        // enforce more diversity for half of the n-best list
        // TODO(sasa): make this more parameterizable
        finalTranslations.add(trans);
        ++nExtracted;
        seenCompl.add(complWord);
      } else {
        finalTranslations.add(trans);
        ++nExtracted;
      }
      if (nExtracted >= size) {
        break;
      }
    }
    while (nExtracted-- > 0) {
      // TODO: pop some local completions, only log for now
      logger.debug("local compl: " + complPQ.poll());
    }
    final long nbestEndTime = System.nanoTime();
    logger.info("nbest time: {} sec", (nbestEndTime - nbestStartTime) / 1e9);

    return finalTranslations;
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
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {
    return translate(scorer, source, sourceInputId, sourceInputProperties,
        outputSpace, targets);
  }

  @Override
  public RichTranslation<TK, FV> translate(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      OutputSpace<TK, FV> outputSpace, List<Sequence<TK>> targets) {

    if (outputSpace != null) outputSpace.setSourceSequence(source);
    
    Beam<Derivation<TK, FV>> beam = decode(scorer, source, sourceInputId, sourceInputProperties,
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
    final private Map<CoverageSet, Beam<Derivation<TK, FV>>> beams = new HashMap<>();
    final private Set<CoverageSet>[] coverageCountToCoverageSets;
    final private RecombinationHistory<Derivation<TK, FV>> recombinationHistory;

    @SuppressWarnings("unchecked")
    public CoverageBeams(int sourceSize,
        RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {
      coverageCountToCoverageSets = new Set[sourceSize + 1];
      for (int i = 0; i < sourceSize + 1; i++) {
        coverageCountToCoverageSets[i] = new HashSet<>();
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
      List<Derivation<TK, FV>> hypothesisList = new LinkedList<>();

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
