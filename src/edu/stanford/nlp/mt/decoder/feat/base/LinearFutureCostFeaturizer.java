package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Linear distortion with optional future cost estimation a la
 * Green et al. (2010).
 * 
 * @author Michel Galley
 */
public class LinearFutureCostFeaturizer extends DerivationFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "LinearDistortion";

  public static final boolean ADD_EOS = Boolean.parseBoolean(System
      .getProperty("addEOS", "false"));
  public static final float DEFAULT_FUTURE_COST_DELAY = 0f;

  public final float futureCostDelay;

  /**
   * Constructor
   */
  public LinearFutureCostFeaturizer() {
    this(DEFAULT_FUTURE_COST_DELAY);
  }

  /**
   * Constructor.
   * 
   * @param futureCostDelay
   */
  public LinearFutureCostFeaturizer(float futureCostDelay) {
    this.futureCostDelay = futureCostDelay;
  }
  
  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public LinearFutureCostFeaturizer(String... args) {
    // Argument determines how much future cost to pay upfront:
    // 1.0 => everything; 0.0 => nothing, as in Moses
    if (args.length == 1) {
      futureCostDelay = Float.parseFloat(args[0]);
      assert (futureCostDelay >= 0.0);
      assert (futureCostDelay <= 1.0);
    } else {
      futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    }
    System.err.println("Future cost delay: " + futureCostDelay);
  }

  private static int lastOptionForeignEdge(Derivation<IString, String> hyp) {
    if (hyp.rule == null) {
      return 0;
    }
    return hyp.rule.sourceCoverage.length();
  }
  
  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    float oldFutureCost = f.prior != null ? ((FutureCostState) f.prior.getState(this)).f
        : 0.0f;
    float futureCost;
    if (f.done) {
      futureCost = 0.0f;
    } else {
      futureCost = (1.0f - futureCostDelay) * futureCost(f) + futureCostDelay
          * oldFutureCost;
    }
    int edge = lastOptionForeignEdge(f.derivation);
    f.setState(this, new FutureCostState(edge, futureCost));
    float deltaCost = futureCost - oldFutureCost;
    return Collections.singletonList(new FeatureValue<String>(FEATURE_NAME, -1.0 * (cost(f) + deltaCost), true));
  }

  @Override
  public void initialize(int sourceInputId,
      Sequence<IString> foreign) {
  }

  static int cost(Featurizable<IString, String> f) {
    int cost = f.linearDistortion;
    if (ADD_EOS)
      cost += getEOSDistortion(f);
    return cost;
  }

  static int futureCost(Featurizable<IString, String> f) {
    int nextWordIndex = f.derivation.rule.sourceCoverage.length();
    int firstGapIndex = f.derivation.sourceCoverage.nextClearBit(0);
    if (firstGapIndex > nextWordIndex)
      firstGapIndex = nextWordIndex;
    int futureCost = nextWordIndex - firstGapIndex;
    // General case:
    // x x . . . x x X . x x x . // x:covered X:last
    // 6 5 4 3 2 1 cost=6 + 3 + 3 = 12
    // j i
    // Special case:
    // [x] [X] [x x] . . . // x:covered X:`last
    // x x 2 + fc=6 delta=6
    // x 4 + fc=2 delta=-4
    // x 0 + fc=2 delta=0
    // 6 5 4 3 2 1 cost = 3
    // j i
    int p = firstGapIndex - 1;
    while (true) {
      p = f.derivation.sourceCoverage.nextSetBit(p + 1);
      if (p < 0)
        break;
      ++futureCost;
    }
    return futureCost;
  }

  private static int getEOSDistortion(Featurizable<IString, String> f) {
    if (f.done) {
      int endGap = f.sourceSentence.size() - f.rule.sourceCoverage.length();
      assert (endGap >= 0);
      return endGap;
    }
    return 0;
  }
  
  private static class FutureCostState extends FeaturizerState {

    private final float f;
    private final int edge;

    public FutureCostState(int edge, float f) {
      this.edge = edge;
      this.f = f;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof FutureCostState)) {
        return false;
      } else {
        FutureCostState o = (FutureCostState) other;
        return this.edge == o.edge;
      }
    }

    @Override
    public int hashCode() {
      return this.edge;
    }
  }
}
