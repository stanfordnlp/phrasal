package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BundleBeam;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle.Consequent;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.util.Pair;

/**
 * Cube pruning as described by Chiang and Huang (2007).
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class CubePruningDecoder<TK,FV> extends AbstractBeamInferer<TK, FV> {

  private static final Logger logger = LogManager.getLogger(CubePruningDecoder.class.getName());
  
  // 1200 gives roughly the same baseline performance as the default beam size
  // of MultiBeamDecoder
  public static final int DEFAULT_BEAM_SIZE = 1200;
  public static final int DEFAULT_MAX_DISTORTION = -1;

  protected int maxDistortion;
  protected final int defaultDistortion;
  
  static public <TK, FV> CubePruningDecoderBuilder<TK, FV> builder() {
    return new CubePruningDecoderBuilder<TK, FV>();
  }

  protected CubePruningDecoder(CubePruningDecoderBuilder<TK, FV> builder) {
    super(builder);
    maxDistortion = builder.maxDistortion;
    defaultDistortion = builder.maxDistortion;

    if (maxDistortion != -1) {
      logger.info("Cube pruning decoder {}. Distortion limit: {}", builder.decoderId, 
          maxDistortion);
    } else {
      logger.info("Cube pruning decoder {}. No hard distortion limit", builder.decoderId);
    }    
  }

  public static class CubePruningDecoderBuilder<TK, FV> extends AbstractBeamInfererBuilder<TK, FV> {
    int maxDistortion = DEFAULT_MAX_DISTORTION;
    int decoderId = -1;

    @Override
    public AbstractBeamInfererBuilder<TK, FV> setMaxDistortion(int maxDistortion) {
      this.maxDistortion = maxDistortion;
      return this;
    }

    public CubePruningDecoderBuilder() {
      super(DEFAULT_BEAM_SIZE, null);
    }

    @Override
    public Inferer<TK, FV> newInferer() {
      decoderId++;
      return new CubePruningDecoder<TK, FV>(this);
    }

    @Override
    public AbstractBeamInfererBuilder<TK, FV> useITGConstraints(boolean itg) {
      throw new UnsupportedOperationException("ITG constraints are not supported yet");
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int nbest) {

    TimeKeeper timer = TimingUtils.start();
    
    // Set the distortion limit
    if (sourceInputProperties.containsKey(InputProperty.DistortionLimit)) {
      this.maxDistortion = (int) sourceInputProperties.get(InputProperty.DistortionLimit);
      logger.info("Changing distortion limit from {} to {}", this.defaultDistortion, this.maxDistortion);
    } else {
      this.maxDistortion = defaultDistortion;
    }
    
    // TM (phrase table) query for applicable rules
    Pair<Sequence<TK>, List<ConcreteRule<TK,FV>>> sourceRulePair = 
        getRules(source, sourceInputProperties, targets, sourceInputId, scorer);
    source = sourceRulePair.first();
    timer.mark("TM query");
    
    // Check after potential filtering for OOVs
    if (source == null || source.size() == 0) return null;
    final int sourceLength = source.size();
    final List<ConcreteRule<TK,FV>> ruleList = sourceRulePair.second();
        
    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    outputSpace.filter(ruleList, this, sourceInputProperties);
    
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<>(ruleList, source);
    if ( ! ruleGrid.isCoverageComplete()) {
      logger.warn("Incomplete coverage for source input {}", sourceInputId);
    }
    timer.mark("Rulegrid");
    
    // Fill Beam 0 (root)...only has one cube
    BundleBeam<TK,FV> nullBeam = new BundleBeam<>(beamCapacity, filter, ruleGrid, 
          recombinationHistory, maxDistortion, 0);
  
    // Setup the beams
    List<List<ConcreteRule<TK,FV>>> ruleListList = Collections.singletonList(ruleList);
    Derivation<TK, FV> nullHypothesis = new Derivation<>(sourceInputId, source, 
        sourceInputProperties, heuristic, scorer, ruleListList, outputSpace);
    nullBeam.put(nullHypothesis);
    final List<Beam<Derivation<TK,FV>>> beams = new ArrayList<>(sourceLength+1);
    beams.add(nullBeam);
    for(int i = 1; i <= sourceLength; ++i) beams.add(new BundleBeam<>(beamCapacity, filter, ruleGrid, 
        recombinationHistory, maxDistortion, i));

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, source);

    // Prefix decoding: pre-populate the beams.
    int startOfDecoding = 1;
    int minSourceCoverage = 0;
    boolean prefilledBeams = false;
    if (sourceInputProperties.containsKey(InputProperty.TargetPrefix) && targets != null && targets.size() > 0) {
      if (targets.size() > 1) logger.warn("Decoding to multiple prefixes is not supported. Choosing the first one.");
      minSourceCoverage = prefixFillBeams(source, ruleList, sourceInputProperties, targets.get(0), 
          scorer, beams, sourceInputId, outputSpace);
      startOfDecoding = minSourceCoverage + 1;
      prefilledBeams = true;
      timer.mark("Prefill");
      // WSGDEBUG
//      System.err.printf("PREFILLING: %d %d%n", minSourceCoverage, startOfDecoding);
    }
    
    // main translation loop---beam expansion
    final int maxPhraseLength = phraseGenerator.maxLengthSource();
    int totalHypothesesGenerated = 1, numRecombined = 0, numPruned = 0;
    for (int i = startOfDecoding; i <= sourceLength; i++) {
      int rootBeam = prefilledBeams ? minSourceCoverage : 0;
      int minCoverage = i - maxPhraseLength;
      int startBeam = Math.max(rootBeam, minCoverage);

      // Initialize the priority queue
      Queue<Item<TK,FV>> pq = new PriorityQueue<>(beamCapacity);
      for (int j = startBeam; j < i; ++j) {
        BundleBeam<TK,FV> bundleBeam = (BundleBeam<TK,FV>) beams.get(j);
        for (HyperedgeBundle<TK,FV> bundle : bundleBeam.getBundlesForConsequentSize(i)) {
          List<Item<TK,FV>> consequents = generateConsequentsFrom(null, bundle, sourceInputId, outputSpace);
          pq.addAll(consequents);
          totalHypothesesGenerated += consequents.size();
        }
      }

      BundleBeam<TK,FV> newBeam = (BundleBeam<TK, FV>) beams.get(i);
      int numPoppedItems = newBeam.size();      
      while (numPoppedItems < beamCapacity && ! pq.isEmpty()) {
        Item<TK,FV> item = pq.poll();

        // WSGDEBUG
        //        System.err.printf("BEAM %d STATUS%n", i);
        //        System.err.println(newBeam.beamString());
        //        System.err.println("===========");

        newBeam.put(item.derivation);

        List<Item<TK,FV>> consequents = generateConsequentsFrom(item.consequent, item.consequent.bundle, 
            sourceInputId, outputSpace);
        pq.addAll(consequents);
        totalHypothesesGenerated += consequents.size();

        ++numPoppedItems;
      }

      // WSGDEBUG
      //    System.err.printf("BEAM %d STATUS%n", i);
      //    System.err.println(newBeam.beamString());
      //    System.err.println("===========");

      numRecombined += newBeam.recombined();
    }
    timer.mark("Inference");
    
    // Debug statistics
    logger.info("input {}: Decoding time: {}", sourceInputId, timer);
    logger.info("input {}: #derivations generated: {}", sourceInputId, totalHypothesesGenerated);
    logger.info("input {}: #recombined: {}", sourceInputId, numRecombined);
    logger.info("input {}: #pruned by output constraint: {}", sourceInputId, numPruned);

    // Return the best beam, which should be the goal beam
    boolean isGoalBeam = true;
    for (int i = beams.size()-1; i >= 0; --i) {
      Beam<Derivation<TK,FV>> beam = beams.get(i);
      if (beam.size() != 0) {
        Featurizable<TK,FV> bestHyp = beam.iterator().next().featurizable;
        
        // WSGDEBUG
//        System.err.println(targets.get(0).toString());
//        System.err.println(bestHyp.derivation.score);
//        System.err.println(bestHyp.derivation.historyString());
//        System.err.println("=========");
        
        if (outputSpace.allowableFinal(bestHyp)) {
          if ( ! isGoalBeam) {
            final int coveredTokens = sourceLength - bestHyp.numUntranslatedSourceTokens;
            logger.warn("input {}: DECODER FAILURE, but backed off to coverage {}/{}: ", sourceInputId,
                coveredTokens, sourceLength);
          }
          return beam;
        }
      }
      isGoalBeam = false;
    }

    logger.warn("input {}: DECODER FAILURE", sourceInputId);
    return null;
  }

  /**
   * Searches for consequents, always returning at least one and at most two.
   * 
   * @param antecedent
   * @param bundle
   * @param sourceInputId
   * @param outputSpace
   * @return
   */
  private List<Item<TK, FV>> generateConsequentsFrom(Consequent<TK, FV> antecedent, 
      HyperedgeBundle<TK, FV> bundle, int sourceInputId, OutputSpace<TK, FV> outputSpace) {
    List<Item<TK,FV>> consequents = new ArrayList<>();
    List<Consequent<TK,FV>> successors = new LinkedList<>(bundle.nextSuccessors(antecedent));
    while (successors.size() > 0) {
      Consequent<TK,FV> successor = successors.remove(0);
      boolean buildDerivation = outputSpace.allowableContinuation(successor.antecedent.featurizable, 
          successor.rule);
      if (buildDerivation) {
        // Derivation construction: this is the expensive part
        Derivation<TK, FV> derivation = new Derivation<>(sourceInputId,
            successor.rule, successor.antecedent.length, successor.antecedent, featurizer, scorer, 
            heuristic, outputSpace);
        consequents.add(new Item<>(derivation, successor));
        
      } else {
        // Pruned by output constraint. Keep searching in the bundle.
        successors.addAll(bundle.nextSuccessors(successor));
      }
    }
    return consequents;
  }

  /**
   * Wrapper for class for the priority queue that organizes successors.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
  protected static class Item<TK,FV> implements Comparable<Item<TK,FV>> {
    public final Derivation<TK, FV> derivation;
    public final Consequent<TK, FV> consequent;

    public Item(Derivation<TK,FV> derivation, Consequent<TK,FV> consequent) {
      assert derivation != null;
      this.derivation = derivation;
      this.consequent = consequent;
    }

    @Override
    public int compareTo(Item<TK,FV> o) {
      return derivation.compareTo(o.derivation);
    }
    
    @Override
    public String toString() {
      return derivation.toString();
    }
  }

  @Override
  public void dump(Derivation<TK, FV> hyp) {
    throw new UnsupportedOperationException();
  }
}
