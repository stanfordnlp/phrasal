package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.DTUTable;

/**
 * @author Michel Galley
 */
public class DTULinearDistortionFeaturizer extends
    DerivationFeaturizer<IString, String> implements RuleFeaturizer<IString,String> {

  public static final String DEBUG_PROPERTY = "DebugDTULinearDistortionFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static final String LD_FEATURE_NAME = "LinearDistortion";
  public static final String SG_FEATURE_NAME = "SG:Length";

  public final float futureCostDelay;

  public static final float DEFAULT_FUTURE_COST_DELAY = Float.parseFloat(System
      .getProperty("dtuFutureCostDelay", "0f"));

  public DTULinearDistortionFeaturizer() {
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    if (futureCostDelay != 0.0)
      System.err.println("Future cost delay: " + futureCostDelay);
  }

  public DTULinearDistortionFeaturizer(String... args) {
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

    if (f instanceof DTUFeaturizable)
      if (f.getSegmentIdx() > 0) {
        f.setState(this, f.prior.getState(this));
        return null;
      }

    // /////////////////////////////////////////
    // (1) Source gap lengths:
    // /////////////////////////////////////////

    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(2);
    int span = f.rule.sourceCoverage.length()
        - f.rule.sourceCoverage.nextSetBit(0);
    int totalSz = 0;
    for (IString fw : f.sourcePhrase)
      if (fw.id != DTUTable.GAP_STR.id)
        ++totalSz;
    int gapSz = span - totalSz;
    if (gapSz != 0)
      list.add(new FeatureValue<String>(SG_FEATURE_NAME, -1.0 * gapSz));

    // /////////////////////////////////////////
    // (2) Standard linear distortion features:
    // /////////////////////////////////////////

    float oldFutureCost = f.prior != null ? ((FutureCostState) f.prior.getState(this)).f
        : 0.0f;
    float futureCost;
    if (f.done) {
      futureCost = 0.0f;
    } else {
      futureCost = (1.0f - futureCostDelay)
          * LinearFutureCostFeaturizer.futureCost(f) + futureCostDelay
          * oldFutureCost;
    }
    int edge = lastOptionForeignEdge(f.derivation);
    f.setState(this, new FutureCostState(edge, futureCost));
    float deltaCost = futureCost - oldFutureCost;
    int cost = LinearFutureCostFeaturizer.cost(f);
    list.add(new FeatureValue<String>(LD_FEATURE_NAME, -1.0
        * (cost + deltaCost)));

    return list;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(1);
    int minTotalSz = 0;
    for (IString fw : f.sourcePhrase)
      if (fw.id == DTUTable.GAP_STR.id)
        ++minTotalSz;
    if (minTotalSz > 0)
      list.add(new FeatureValue<String>(SG_FEATURE_NAME, -1.0 * minTotalSz));
    return list;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
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
