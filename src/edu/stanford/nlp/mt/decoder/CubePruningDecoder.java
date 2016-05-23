package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.Collections;
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
import edu.stanford.nlp.mt.decoder.util.SyntheticRules;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

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

  // TODO(spenceg) May need to cap the number of popped items to keep it from running forever.
  
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
    
    boolean printDebug = false; // sourceInputId == 1022;
    
    // Set the distortion limit
    if (sourceInputProperties.containsKey(InputProperty.DistortionLimit)) {
      this.maxDistortion = (int) sourceInputProperties.get(InputProperty.DistortionLimit);
      logger.info("input {}: Changing distortion limit from {} to {}", sourceInputId, 
          this.defaultDistortion, this.maxDistortion);
    } else {
      this.maxDistortion = defaultDistortion;
    }
    InputProperties inputProperties = sourceInputProperties;
    if(foregroundModel != null && !inputProperties.containsKey(InputProperty.ForegroundTM)) {
      inputProperties.put(InputProperty.ForegroundTM, foregroundModel);
    }
    
    // TM (phrase table) query for applicable rules
    final PhraseQuery<TK,FV> phraseQuery = 
        getRules(source, inputProperties, targets, sourceInputId, scorer);
    source = phraseQuery.filteredSource;
    timer.mark("TM query");
    
    // Check after potential filtering for OOVs
    if (source.size() == 0) return null;
    final int sourceLength = source.size();
    final List<ConcreteRule<TK,FV>> ruleList = phraseQuery.ruleList;
    logger.info("input {}: rule query size {}", sourceInputId, ruleList.size());
    
    if (printDebug) {
      for (ConcreteRule<TK,FV> rule : ruleList)
        System.err.println(rule);
    }
    
    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    outputSpace.filter(ruleList, this, inputProperties);
    
    assert inputProperties.containsKey(InputProperty.RuleQueryLimit);
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<>(ruleList, source, 
        (int) inputProperties.get(InputProperty.RuleQueryLimit));
    if ( ! ruleGrid.isCoverageComplete()) {
      logger.warn("input {}: Incomplete source coverage", sourceInputId);
    }
    timer.mark("Rulegrid");
    
    // Fill Beam 0 (root)...only has one cube
    BundleBeam<TK,FV> nullBeam = new BundleBeam<>(beamCapacity, filter, ruleGrid, 
          recombinationHistory, maxDistortion, 0);
  
    // Setup the beams
    List<List<ConcreteRule<TK,FV>>> ruleListList = Collections.singletonList(ruleList);
    Derivation<TK, FV> nullHypothesis = new Derivation<>(sourceInputId, source, 
        inputProperties, heuristic, scorer, ruleListList, outputSpace);
    nullBeam.put(nullHypothesis);
    final List<Beam<Derivation<TK,FV>>> beams = new ArrayList<>(sourceLength+1);
    beams.add(nullBeam);
    for (int i = 1; i <= sourceLength; ++i) {
      beams.add(new BundleBeam<>(beamCapacity, filter, ruleGrid, recombinationHistory, maxDistortion, i));
    }

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, source);
    
    // Prefix decoding
    int startOfDecoding = 1;
    int minSourceCoverage = 0;
    boolean prefilledBeams = false;
    if (inputProperties.containsKey(InputProperty.TargetPrefix) && targets != null && targets.size() > 0) {
      if (targets.size() > 1) logger.warn("Decoding to multiple prefixes is not supported. Choosing the first one.");
      minSourceCoverage = decodePrefix(source, ruleList, inputProperties, targets.get(0), 
          scorer, beams, sourceInputId, outputSpace, recombinationHistory, timer);
      if (minSourceCoverage < 0) {
        logger.warn("input {}: PREFIX DECODING FAILURE", sourceInputId);
        return null;
      }
      startOfDecoding = minSourceCoverage + 1;
      prefilledBeams = true;
      timer.mark("Prefix Decoding");
    }
  
    //System.err.println("start main translation loop");
    // main translation loop---beam expansion
    final int maxPhraseLength = phraseGenerator.maxLengthSource();
    int totalHypothesesGenerated = 1, numRecombined = 0, numPruned = 0;
    for (int i = startOfDecoding; i <= sourceLength; i++) {
      int rootBeam = prefilledBeams ? minSourceCoverage : 0;
      int minCoverage = i - maxPhraseLength;
      int startBeam = Math.max(rootBeam, minCoverage);

      // Initialize the priority queue
      Queue<Item> pq = new PriorityQueue<>(2*beamCapacity);
      for (int j = startBeam; j < i; ++j) {
        BundleBeam<TK,FV> bundleBeam = (BundleBeam<TK,FV>) beams.get(j);
        for (HyperedgeBundle<TK,FV> bundle : bundleBeam.getBundlesForConsequentSize(i)) {
          for(Item consequent : generateConsequentsFrom(null, bundle, sourceInputId, outputSpace, false)) {
            ++totalHypothesesGenerated;
            if (consequent.derivation == null) ++numPruned;
            pq.add(consequent);
          }
        }
      }

      // Beam-filling
      BundleBeam<TK,FV> newBeam = (BundleBeam<TK, FV>) beams.get(i);
      int numPoppedItems = newBeam.size();
      while (numPoppedItems < beamCapacity && ! pq.isEmpty()) {
        final Item item = pq.poll();

        // Derivations are null if they're pruned by an output constraint.
        if (item.derivation != null && (Double.isInfinite(item.derivation.score) || Double.isNaN(item.derivation.score))) {
          // this normally happens when there's something brain dead about
          // the baseline model/featurizers,
          // like log(p) values that equal -inf for some featurizers.
          logger.warn("Generated derivation with invalid score: {}", item.derivation);
          ++numPoppedItems;
        } else if (item.derivation != null) {
          newBeam.put(item.derivation);
          ++numPoppedItems;
        }
        // else pruned items don't count against the pop limit

        // Expand this consequent.
        for(Item consequent : generateConsequentsFrom(item.consequent, item.consequent.bundle, 
            sourceInputId, outputSpace, false)) {
          ++totalHypothesesGenerated;
          if (consequent.derivation == null) ++numPruned;
          pq.add(consequent);
        }
      }
          
      if (printDebug) {
        System.err.println(newBeam.beamString(10));
      }
      
      numRecombined += newBeam.recombined();
    }
    timer.mark("Inference");
    
    // Debug statistics
    logger.info("input {}: Decoding time: {}", sourceInputId, timer);
    logger.info("input {}: #derivations generated: {}  pruned: {}  recombined: {}", sourceInputId, 
        totalHypothesesGenerated, numPruned, numRecombined);

    // Return the best beam, which should be the goal beam
    boolean isGoalBeam = true;
    for (int i = beams.size()-1; i >= 0; --i) {
      Beam<Derivation<TK,FV>> beam = beams.get(i);
      if (beam.size() != 0) {
        Featurizable<TK,FV> bestHyp = beam.iterator().next().featurizable;
        
        if (printDebug) {
          System.err.println(bestHyp.derivation.historyString());
        }
               
        if (outputSpace.allowableFinal(bestHyp)) {
          if ( ! isGoalBeam) {
            final int coveredTokens = sourceLength - bestHyp.numUntranslatedSourceTokens;
            logger.warn("input {}: DECODER FAILURE, but backed off to coverage {}/{}: ", sourceInputId,
                coveredTokens, sourceLength);
          }
          
          if (printDebug) {
            Derivation<TK, FV> best = beam.iterator().next();
            logger.info("input {}: best derivation {}", sourceInputId, best.historyString());
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
   * @param checkSourceCoverage
   * @return
   */
  private List<Item> generateConsequentsFrom(Consequent<TK, FV> antecedent, 
      HyperedgeBundle<TK, FV> bundle, int sourceInputId, OutputSpace<TK, FV> outputSpace, 
      boolean checkSourceCoverage) {
    List<Item> successors = new ArrayList<>(2);
    for(Consequent<TK, FV> successor : bundle.nextSuccessors(antecedent)) {
      boolean buildDerivation = outputSpace.allowableContinuation(successor.antecedent.featurizable, successor.rule)
          && (!checkSourceCoverage || (!successor.antecedent.sourceCoverage.intersects(successor.rule.sourceCoverage) ));
      Derivation<TK, FV> derivation = buildDerivation ? new Derivation<>(sourceInputId,
          successor.rule, successor.antecedent.length, successor.antecedent, featurizer, scorer, 
          heuristic, outputSpace) : null;
      successors.add(new Item(derivation, successor));
    }
    return successors;
  }
  
  private int itemId = 0;
  
  /**
   * Wrapper for class for the priority queue that organizes successors.
   * 
   * @author Spence Green
   *
   * @param <TK>
   * @param <FV>
   */
  protected class Item implements Comparable<Item> {
    
    public final Derivation<TK, FV> derivation;
    public final Consequent<TK, FV> consequent;
    public int id = itemId++;

    public Item(Derivation<TK,FV> derivation, Consequent<TK,FV> consequent) {
      this.derivation = derivation;
      this.consequent = consequent;
    }

    @Override
    public int compareTo(Item o) {
      if (derivation == null && o.derivation == null) {
        return id - o.id;
      } else if (derivation == null) {
        return 1;
      } else if (o.derivation == null) {
        return -1;
      } else {
        return derivation.compareTo(o.derivation);
      }
    }
    
    @Override
    public String toString() {
      return String.format("%d: %s", id, derivation);
    }
  }

  @Override
  public void dump(Derivation<TK, FV> hyp) {
    throw new UnsupportedOperationException();
  }
  
  
  
  /**
   * Populate the beams given the prefix. Returns the minimum source coverage cardinality.
   * 
   * @param source
   * @param ruleList
   * @param sourceInputProperties
   * @param prefix
   * @param scorer
   * @param beams
   * @return The beam at which standard decoding should begin.
   */
  @SuppressWarnings("unchecked")
  private int decodePrefix(Sequence<TK> source, List<ConcreteRule<TK,FV>> ruleList, 
      InputProperties sourceInputProperties, Sequence<TK> prefix, Scorer<FV> scorer, 
      List<Beam<Derivation<TK,FV>>> beams, int sourceInputId, OutputSpace<TK, FV> outputSpace,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory, TimeKeeper timer) {
    if (source == null || source.size() == 0 || prefix == null || prefix.size() == 0) return 0;

    //System.err.println("start prefix decoding");
    boolean printDebug = false; // sourceInputId == 1022;

    boolean allowIncompletePrefix = false;
    if (sourceInputProperties.containsKey(InputProperty.AllowIncompletePrefix)) {
      allowIncompletePrefix = (boolean) sourceInputProperties.get(InputProperty.AllowIncompletePrefix);
    }
    
    int ruleQueryLimit = -1;  // Disable query limit. We might need some of these rules.
    final RuleGrid<TK,FV> prefixGrid = new RuleGrid<>(ruleList, source, prefix, ruleQueryLimit, allowIncompletePrefix); 
    
    //System.err.println("created prefix rule grid");

    // Add new rules to the rule grid
    SyntheticRules.augmentPrefixRuleGrid(prefixGrid, prefix, sourceInputId, source, this, sourceInputProperties, prefixAlignCompounds);
    timer.mark("PrefixAug");
    
    //System.err.println("augmented prefix rule grid. maxTgtLength = " + prefixGrid.maxTargetLength());;
    
    int prefixLength = prefix.size();
    
    //System.err.println("prefixLength = " + prefixLength + "; prefixGrid.gridDimension() = " + prefixGrid.gridDimension());
    
    final List<Beam<Derivation<TK,FV>>> tgtBeams = new ArrayList<>(prefixLength + 1);
    
    // null beam
    BundleBeam<TK,FV> nullBeam = new BundleBeam<>(beamCapacity, filter, prefixGrid, recombinationHistory, maxDistortion, 0, true);
    for(Derivation<TK,FV> d : beams.get(0)) nullBeam.put(d, false);
    tgtBeams.add(nullBeam);
    
    for (int i = 1; i <= prefixLength; ++i) {
      tgtBeams.add(new BundleBeam<>(beamCapacity, filter, prefixGrid, recombinationHistory, maxDistortion, i, true));
    }
    
    final int maxTgtPhraseLength = prefixGrid.maxTargetLength();
    int totalHypothesesGenerated = 1, numRecombined = 0, numPruned = 0;
    int lastRecoveredCardinality = 0;
    for (int i = 1; i <= prefixLength; ++i) {
      //System.err.println("i = " + i);
      int rootBeam = 0;
      int minCoverage = i - maxTgtPhraseLength;
      int startBeam = Math.max(rootBeam, minCoverage);

      // Initialize the priority queue
      Queue<Item> pq = new PriorityQueue<>(2*beamCapacity);
      for (int j = startBeam; j < i; ++j) {
        BundleBeam<TK,FV> bundleBeam = (BundleBeam<TK,FV>) tgtBeams.get(j);
        //System.err.println("card " + j + " consequent size " + i);
        for (HyperedgeBundle<TK,FV> bundle : bundleBeam.getBundlesForConsequentSize(i)) {
          for(Item consequent : generateConsequentsFrom(null, bundle, sourceInputId, outputSpace, true)) {
            ++totalHypothesesGenerated;
            if (consequent.derivation == null) ++numPruned;
            pq.add(consequent);
          }
        }
      }
      
      // Beam-filling
      BundleBeam<TK,FV> newBeam = (BundleBeam<TK, FV>) tgtBeams.get(i);
      int numPoppedItems = newBeam.size();
      while (numPoppedItems < beamCapacity && ! pq.isEmpty()) {
        final Item item = pq.poll();

        // Derivations are null if they're pruned by an output constraint or have incompatible source coverage.
        if (item.derivation != null) {
          newBeam.put(item.derivation);
          ++numPoppedItems;
        }
       
        // Expand this consequent
        for(Item consequent : generateConsequentsFrom(item.consequent, item.consequent.bundle, 
            sourceInputId, outputSpace, true)) {
          ++totalHypothesesGenerated;
          if (consequent.derivation == null) ++numPruned;
          pq.add(consequent);
        }
      }
      
      //System.err.println("beam " + i + ": " + newBeam.size());
      
      if(i == prefixLength && newBeam.size() == 0) {
        // Couldn't figure out how to extend any derivations in the beams. Walk back from this point
        // to the first beam that has valid derivations in. Try to reset that beam by extending each
        // derivation with target insertion rules.
        int j;
        for (j = i-1; j >= 0; --j) if (tgtBeams.get(j).size() > 0) break;
        
        if(j > lastRecoveredCardinality) {
          lastRecoveredCardinality = j;
          boolean derivationsExtended = false;
          
          logger.info("No hypothesis for complete prefix found. Recovering by moving back to cardinality " + j + " and augmenting phrase grid for target word: " + prefix.get(j));
          
          Sequence<TK> extension = prefix.subsequence(j, j + 1);
          int numRules = SyntheticRules.recoverAugmentPrefixRuleGrid(prefixGrid, extension, sourceInputId, source, this, sourceInputProperties);
          derivationsExtended = derivationsExtended || numRules > 0;
  
          // sg: Reset search. This is some scary shit.
          // jw: I don't actually think it is that scary. Because all beams with cardinality > j are empty, nothing happened in between that we could be messing up.
          if (derivationsExtended) {
            i = j;
            for(; j <= prefixLength; ++j)
              ((BundleBeam<TK,FV>) tgtBeams.get(j)).reset();
          } // else we can't make any more progress, so continue with decoding, which will fail.
        }
        else {
          logger.warn("No hypothesis for complete prefix found, but already tried to recover from cardinality " + j + ". Giving up.");
        }
      }
      
      if (printDebug) {
        System.err.println(newBeam.beamString(10));
      }
      numRecombined += newBeam.recombined();
    }
    timer.mark("PrefixDecoding");
    
    // Debug statistics
    logger.info("input {}: Prefix decoding time: {}", sourceInputId, timer);
    logger.info("input {}: #derivations generated: {}  pruned: {}  recombined: {}", sourceInputId, 
        totalHypothesesGenerated, numPruned, numRecombined);
    
    return populateSourceBeams(tgtBeams, beams);
  }
  
  // returns the minimum source coverage cardinality
  @SuppressWarnings("unchecked")
  private int populateSourceBeams(List<Beam<Derivation<TK,FV>>> tgtBeams, List<Beam<Derivation<TK,FV>>> srcBeams) {
    //System.err.println("populate source beams");
    int maxTgtBeam = tgtBeams.size() - 1;
    int minSrcCard = 0;

    // We only propagate the final beam to standard source-cardinality search
    for(int i = maxTgtBeam; i > 0; --i) {
      Beam<Derivation<TK,FV>> tgtBeam = tgtBeams.get(i);
      if(tgtBeam.size() == 0) continue;
      
      minSrcCard = Integer.MAX_VALUE;
      for(Derivation<TK, FV> d : tgtBeam) {
        minSrcCard = Math.min(minSrcCard, d.sourceCoverage.cardinality());
        BundleBeam<TK,FV> srcBeam = (BundleBeam<TK,FV>) srcBeams.get(d.sourceCoverage.cardinality());
        if(srcBeam.size() < srcBeam.capacity()) srcBeam.put(d, false);
      }
      break;
    }
    
    //for(int i = 1; i < srcBeams.size(); ++i)
      //System.err.println("src beam " + i + " size = " + srcBeams.get(i).size());
    return minSrcCard;
  }
  
}
