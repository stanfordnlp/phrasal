package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;

/**
 * @author Michel Galley
 */
public class LinearFutureCostFeaturizer extends StatefulFeaturizer<IString, String> implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugStatefulLinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public final float futureCostDelay;

  public static final String FEATURE_NAME = "LinearDistortion";

  public static final float DEFAULT_FUTURE_COST_DELAY = Float.parseFloat(System.getProperty("futureCostDelay","0.5f"));

  public LinearFutureCostFeaturizer() {
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    System.err.println("Future cost delay: "+futureCostDelay);
  }

  public LinearFutureCostFeaturizer(String... args) {
		// Argument determines how much future cost to pay upfront:
		// 1.0 => everything; 0.0 => nothing, as in Moses
    if(args.length == 1) {
      futureCostDelay = 1.0f - Float.parseFloat(args[0]);
      assert(futureCostDelay >= 0.0);
      assert(futureCostDelay <= 1.0);
    } else {
      futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    }
    System.err.println("Future cost delay: "+futureCostDelay);
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;
    float futureCost;
    if(f.done) {
      futureCost = 0.0f;
    } else {
      int firstGapIndex = f.hyp.foreignCoverage.nextClearBit(0);
      futureCost = f.hyp.translationOpt.foreignCoverage.length()-firstGapIndex;
      // x x . . . x x x .
      //     6 5 4 3 2 1 cost=6
      //     j           i
      // where i = f.hyp.translationOpt.foreignCoverage.length()
      //       j = f.hyp.foreignCoverage.nextClearBit(0)
      int p = firstGapIndex;
      for (;;) {
        p = f.hyp.foreignCoverage.nextSetBit(p+1);
        if (p < 0)
          break;
        ++futureCost;
      }
      futureCost = (1.0f-futureCostDelay)*futureCost + futureCostDelay*oldFutureCost;
      f.setState(this, futureCost);
      //System.err.printf("cs=%s pos=%d fc=%d\n", f.hyp.foreignCoverage, f.foreignPosition, (int)futureCost);
    }
    float deltaCost = futureCost - oldFutureCost;
    return new FeatureValue<String>(FEATURE_NAME, -1.0*(f.linearDistortion+deltaCost));
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
