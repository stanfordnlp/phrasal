package edu.stanford.nlp.mt.decoder.inferer.impl;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.inferer.AbstractBeamInferer;
import edu.stanford.nlp.mt.decoder.inferer.AbstractBeamInfererBuilder;
import edu.stanford.nlp.mt.decoder.inferer.Inferer;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.decoder.util.Beam;
import edu.stanford.nlp.mt.decoder.util.BundleBeam;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle;
import edu.stanford.nlp.mt.decoder.util.HyperedgeBundle.Consequent;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.OptionGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
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

  // 800 gives roughly the same baseline performance as the default beam size
  // of MultiBeamDecoder
  public static final int DEFAULT_BEAM_SIZE = 800;
  public static final int DEFAULT_MAX_DISTORTION = -1;

  private final int maxDistortion;

  static public <TK, FV> CubePruningDecoderBuilder<TK, FV> builder() {
    return new CubePruningDecoderBuilder<TK, FV>();
  }

  protected CubePruningDecoder(CubePruningDecoderBuilder<TK, FV> builder) {
    super(builder);
    maxDistortion = builder.maxDistortion;

    if (maxDistortion != -1) {
      System.err.printf("Cube pruning decoder. Distortion limit: %d%n",
          maxDistortion);
    } else {
      System.err.println("Cube pruning decoder. No hard distortion limit.n");
    }    
  }

  public static class CubePruningDecoderBuilder<TK, FV> extends
  AbstractBeamInfererBuilder<TK, FV> {
    int maxDistortion = DEFAULT_MAX_DISTORTION;

    @Override
    public AbstractBeamInfererBuilder<TK, FV> setMaxDistortion(int maxDistortion) {
      this.maxDistortion = maxDistortion;
      return this;
    }

    public CubePruningDecoderBuilder() {
      super(DEFAULT_BEAM_SIZE, null);
    }

    @Override
    public Inferer<TK, FV> build() {
      return new CubePruningDecoder<TK, FV>(this);
    }

    @Override
    public AbstractBeamInfererBuilder<TK, FV> useITGConstraints(boolean itg) {
      throw new UnsupportedOperationException("ITG constraints are not supported yet");
    }
  }

  @Override
  protected Beam<Hypothesis<TK, FV>> decode(Scorer<FV> scorer,
      Sequence<TK> source, int sourceInputId,
      RecombinationHistory<Hypothesis<TK, FV>> recombinationHistory,
      ConstrainedOutputSpace<TK, FV> constrainedOutputSpace,
      List<Sequence<TK>> targets, int nbest) {
    featurizer.reset();
    final int sourceLength = source.size();

    // create beams. We don't need to store all of them, since the translation
    // lattice is implicitly defined by the hypotheses
    final List<BundleBeam<TK,FV>> beams = Generics.newLinkedList();

    // TM (phrase table) query for applicable rules
    List<ConcreteTranslationOption<TK,FV>> options = phraseGenerator
        .translationOptions(source, targets, sourceInputId, scorer);

    // Force decoding---if it is enabled, then filter the rule set according
    // to the references
    if (constrainedOutputSpace != null) {
      options = constrainedOutputSpace.filterOptions(options);
      System.err
      .printf(
          "Translation options after reduction by output space constraint: %d%n",
          options.size());
    }

    // Create rule lookup chart. Rules can be fetched by span.
    final OptionGrid<TK,FV> optionGrid = new OptionGrid<TK,FV>(options, source, true);

    // Fill Beam 0...only has one cube
    BundleBeam<TK,FV> nullBeam = new BundleBeam<TK,FV>(beamCapacity, filter, optionGrid, 
        recombinationHistory, maxDistortion, 0);
    List<List<ConcreteTranslationOption<TK,FV>>> allOptions = Generics.newArrayList(1);
    allOptions.add(options);
    Hypothesis<TK, FV> nullHypothesis = new Hypothesis<TK, FV>(sourceInputId, source,
        heuristic, scorer, annotators, allOptions);
    nullBeam.put(nullHypothesis);
    beams.add(nullBeam);

    // Initialize feature extractors
    featurizer.initialize(sourceInputId, options, source, scorer.getFeatureIndex());

    // main translation loop---beam expansion
    final int maxPhraseLength = phraseGenerator.longestSourcePhrase();
    int totalHypothesesGenerated = 1;
    final long startTime = System.nanoTime();
    for (int i = 1; i <= sourceLength; i++) {
      // Prune old beams
      int startBeam = Math.max(0, i-maxPhraseLength);
      if (startBeam > 0) beams.remove(0);

      // Initialize the priority queue
      Queue<Item<TK,FV>> pq = new PriorityQueue<Item<TK,FV>>(beamCapacity);
      for (BundleBeam<TK,FV> beam : beams) {
        for (HyperedgeBundle<TK,FV> bundle : beam.getBundlesForConsequentSize(i)) {
          List<Item<TK,FV>> consequents = generateConsequentsFrom(null, bundle, sourceInputId);
          pq.addAll(consequents);
          totalHypothesesGenerated += consequents.size();
        }
      }

      // Populate beam i by popping items and generating successors
      BundleBeam<TK,FV> newBeam = new BundleBeam<TK,FV>(beamCapacity, filter, optionGrid, 
          recombinationHistory, maxDistortion, i);
      while (newBeam.size() < beamCapacity && ! pq.isEmpty()) {
        Item<TK,FV> item = pq.poll();
        newBeam.put(item.hypothesis);
        List<Item<TK,FV>> consequents = generateConsequentsFrom(item.consequent, item.consequent.bundle, sourceInputId);
        pq.addAll(consequents);
        totalHypothesesGenerated += consequents.size();
      }
      beams.add(newBeam);
    }
    System.err.printf("Decoding loop time: %.3f s%n", (System.nanoTime() - startTime) / 1e9);
    System.err.printf("Total hypotheses generated: %d%n", totalHypothesesGenerated);

    // Return the best beam, which should be the goal beam
    boolean isGoalBeam = true;
    Collections.reverse(beams);
    for (Beam<Hypothesis<TK,FV>> beam : beams) {
      if (beam.size() != 0
          && (constrainedOutputSpace == null || constrainedOutputSpace
          .allowableFinal(beam.iterator().next().featurizable))) {

        // TODO(spenceg) This should be an error message
        if ( ! isGoalBeam) {
          System.err.println("WSGDEBUG: Decoder failure for sourceId: " + Integer.toString(sourceInputId));
        }

        return beam;
      }
      isGoalBeam = false;
    }

    // Decoder failure
    return null;
  }

  /**
   * Expands a hyperedge bundle. Returns [0,2] successors.
   * @param consequent 
   * 
   * @param bundle
   * @param sourceInputId
   * @return
   */
  private List<Item<TK, FV>> generateConsequentsFrom(Consequent<TK, FV> antecedent, 
      HyperedgeBundle<TK, FV> bundle, int sourceInputId) {
    List<Item<TK,FV>> consequents = Generics.newArrayList(2);
    List<Consequent<TK,FV>> successors = bundle.nextSuccessors(antecedent);
    for (Consequent<TK,FV> successor : successors) {
      // Hypothesis generation
      Hypothesis<TK, FV> newHyp = new Hypothesis<TK, FV>(sourceInputId,
          successor.rule, successor.antecedent.length, successor.antecedent, featurizer, scorer, heuristic);
      consequents.add(new Item<TK,FV>(newHyp, successor));
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
  private static class Item<TK,FV> implements Comparable<Item<TK,FV>> {
    public final Hypothesis<TK, FV> hypothesis;
    public final Consequent<TK, FV> consequent;

    public Item(Hypothesis<TK,FV> hypothesis, Consequent<TK,FV> consequent) {
      this.hypothesis = hypothesis;
      this.consequent = consequent;
    }

    @Override
    public int compareTo(Item<TK,FV> o) {
      return this.hypothesis.compareTo(o.hypothesis);
    }
    
    @Override
    public String toString() {
      return hypothesis.toString();
    }
  }

  @Override
  public void dump(Hypothesis<TK, FV> hyp) {
    throw new UnsupportedOperationException();
  }
}
