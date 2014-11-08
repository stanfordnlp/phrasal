package edu.stanford.nlp.mt.decoder;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.logging.Logger;

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
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SystemLogger;
import edu.stanford.nlp.mt.util.SystemLogger.LogName;
import edu.stanford.nlp.util.Generics;

/**
 * Cube pruning as described by Chiang and Huang (2007)
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class CubePruningDecoder<TK,FV> extends AbstractBeamInferer<TK, FV> {

  // 1200 gives roughly the same baseline performance as the default beam size
  // of MultiBeamDecoder
  public static final int DEFAULT_BEAM_SIZE = 1200;
  public static final int DEFAULT_MAX_DISTORTION = -1;

  protected final int maxDistortion;
  protected final Logger logger;

  static public <TK, FV> CubePruningDecoderBuilder<TK, FV> builder() {
    return new CubePruningDecoderBuilder<TK, FV>();
  }

  protected CubePruningDecoder(CubePruningDecoderBuilder<TK, FV> builder) {
    super(builder);
    maxDistortion = builder.maxDistortion;
    logger = Logger.getLogger(CubePruningDecoder.class.getSimpleName() + String.valueOf(builder.decoderId));
    SystemLogger.attach(logger, LogName.DECODE);

    if (maxDistortion != -1) {
      System.err.printf("Cube pruning decoder %d. Distortion limit: %d%n", builder.decoderId, 
          maxDistortion);
    } else {
      System.err.printf("Cube pruning decoder %d. No hard distortion limit%n", builder.decoderId);
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

  @Override
  protected Beam<Derivation<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      InputProperties sourceInputProperties,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      OutputSpace<TK, FV> outputSpace,
      List<Sequence<TK>> targets, int nbest) {
    final int sourceLength = source.size();

    // create beams. We don't need to store all of them, since the translation
    // lattice is implicitly defined by the hypotheses
    final List<BundleBeam<TK,FV>> beams = Generics.newLinkedList();

    // TM (phrase table) query for applicable rules
    List<ConcreteRule<TK,FV>> ruleList = phraseGenerator
        .getRules(source, sourceInputProperties, targets, sourceInputId, scorer);

    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    final int originalLength = ruleList.size();
    ruleList = outputSpace.filter(ruleList);
    logger.info(String.format("input %d: Rule list after pruning by output constraint: %d/%d",
        sourceInputId, ruleList.size(), originalLength));
    
    // Create rule lookup chart. Rules can be fetched by span.
    final RuleGrid<TK,FV> ruleGrid = new RuleGrid<TK,FV>(ruleList, source, true);

    // Fill Beam 0...only has one cube
    BundleBeam<TK,FV> nullBeam = new BundleBeam<TK,FV>(beamCapacity, filter, ruleGrid, 
        recombinationHistory, maxDistortion, 0);
    List<List<ConcreteRule<TK,FV>>> allOptions = Generics.newArrayList(1);
    allOptions.add(ruleList);
    Derivation<TK, FV> nullHypothesis = new Derivation<TK, FV>(sourceInputId, source, sourceInputProperties, 
        heuristic, scorer, allOptions);
    nullBeam.put(nullHypothesis);
    beams.add(nullBeam);

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, ruleList, source);

    // main translation loop---beam expansion
    final int maxPhraseLength = phraseGenerator.longestSourcePhrase();
    int totalHypothesesGenerated = 1;
    int numRecombined = 0;
    int numPruned = 0;
    final long startTime = System.nanoTime();
    for (int i = 1; i <= sourceLength; i++) {
      // Prune old beams
      int startBeam = Math.max(0, i-maxPhraseLength);
      if (startBeam > 0) beams.remove(0);

      // Initialize the priority queue
      Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(beamCapacity);
      for (BundleBeam<TK,FV> beam : beams) {
        for (HyperedgeBundle<TK,FV> bundle : beam.getBundlesForConsequentSize(i)) {
          List<Item<TK,FV>> consequents = generateConsequentsFrom(null, bundle, sourceInputId, outputSpace);
          pq.addAll(consequents);
          totalHypothesesGenerated += consequents.size();
        }
      }

      // Populate beam i by popping items and generating successors
      BundleBeam<TK,FV> newBeam = new BundleBeam<TK,FV>(beamCapacity, filter, ruleGrid, 
          recombinationHistory, maxDistortion, i);
      boolean outputConstraintsEnabled = false;
      int numPoppedItems = 0;
      while (numPoppedItems < beamCapacity && ! pq.isEmpty()) {
        Item<TK,FV> item = pq.poll();

        // Derivations can be null if the output space is constrained. This means that the derivation for this
        // item was not allowable and thus was not built. However, we need to maintain the consequent
        // so that we can generate successors.
        if (item.derivation == null) {
          ++numPruned;
        } else {
          newBeam.put(item.derivation);
        }
        outputConstraintsEnabled = outputConstraintsEnabled || item.derivation == null;
        
        List<Item<TK,FV>> consequents = generateConsequentsFrom(item.consequent, item.consequent.bundle, 
            sourceInputId, outputSpace);
        pq.addAll(consequents);
        totalHypothesesGenerated += consequents.size();
        
        if (outputConstraintsEnabled && numPoppedItems == beamCapacity-1 && newBeam.size() < sourceLength - i) {
          // Search until we build at least one derivation or the priority queue
          // is exhausted
          continue;
        } else {
          ++numPoppedItems;
        }
      }
      beams.add(newBeam);
      numRecombined += newBeam.recombined();
    }
    
    // Debug statistics
    final double elapsedTime = (System.nanoTime() - startTime) / 1e9;
    logger.info(String.format("input %d: Decoding time: %.3fsec", sourceInputId, elapsedTime));
    logger.info(String.format("input %d: #derivations generated: %d", sourceInputId, totalHypothesesGenerated));
    logger.info(String.format("input %d: #recombined: %d", sourceInputId, numRecombined));
    logger.info(String.format("input %d: #pruned by output constraint: %d", sourceInputId, numPruned));

    // Return the best beam, which should be the goal beam
    boolean isGoalBeam = true;
    Collections.reverse(beams);
    for (Beam<Derivation<TK,FV>> beam : beams) {
      if (beam.size() != 0) {
        Featurizable<TK,FV> bestHyp = beam.iterator().next().featurizable;
        if (outputSpace.allowableFinal(bestHyp)) {
          if ( ! isGoalBeam) {
            final int coveredTokens = sourceLength - bestHyp.numUntranslatedSourceTokens;
            logger.warning(String.format("input %d: DECODER FAILURE, but backed off to coverage %d/%d: ", sourceInputId,
                coveredTokens, sourceLength));
          }
          return beam;
        }
      }
      isGoalBeam = false;
    }

    logger.warning(String.format("input %d: DECODER FAILURE", sourceInputId));
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
    List<Item<TK,FV>> consequents = Generics.newArrayList(2);
    List<Consequent<TK,FV>> successors = bundle.nextSuccessors(antecedent);
    for (Consequent<TK,FV> successor : successors) {
      boolean buildDerivation = outputSpace.allowableContinuation(successor.antecedent.featurizable, successor.rule);
    
      // Derivation construction: this is the expensive part
      Derivation<TK, FV> derivation = buildDerivation ? new Derivation<TK, FV>(sourceInputId,
          successor.rule, successor.antecedent.length, successor.antecedent, featurizer, scorer, heuristic) :
            null;
      consequents.add(new Item<TK,FV>(derivation, successor));
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
      this.derivation = derivation;
      this.consequent = consequent;
    }

    @Override
    public int compareTo(Item<TK,FV> o) {
      if (derivation == null && o.derivation == null) {
        return 0;
      } else if (derivation == null) {
        return -1;
      } else if (o.derivation == null) {
        return 1;
      }
      return this.derivation.compareTo(o.derivation);
    }
    
    @Override
    public String toString() {
      return derivation == null ? "<<NULL>>" : derivation.toString();
    }
  }

  @Override
  public void dump(Derivation<TK, FV> hyp) {
    throw new UnsupportedOperationException();
  }
}
