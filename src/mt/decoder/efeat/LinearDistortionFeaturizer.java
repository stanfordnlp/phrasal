package mt.decoder.efeat;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.StatefulFeaturizer;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 * @author Michel Galley
 */
public class LinearDistortionFeaturizer extends StatefulFeaturizer<IString, String> implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugStatefulLinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  // Purposedely the same name as in mt.decoder.feat.LinearDistortionFeaturizer:
  public static final String FEATURE_NAME = "LinearDistortion";

  public final float futureCostDelay;
  public static final float DEFAULT_FUTURE_COST_DELAY = 0.75f;

  public LinearDistortionFeaturizer() {
    // Disable "standard" LinearDistortion:
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
  }

  public LinearDistortionFeaturizer(String... args) {
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    if(args.length == 1) {
      // Determine how much future cost to pay upfront:
      // 1.0 => everything
      // 0.0 => nothing, as in Moses
      futureCostDelay = 1.0f - Float.parseFloat(args[0]);
      assert(futureCostDelay >= 0.0);
      assert(futureCostDelay <= 1.0);
    } else {
      futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;
    float futureCost;
    if(f.done) {
      futureCost = 0.0f;
    } else {
      int firstGapIndex = f.foreignCoverage.nextClearBit(0);
      int minFutureDistortion = f.foreignPosition-firstGapIndex;
      futureCost = (minFutureDistortion > 0) ? (1+2*minFutureDistortion) : 0;
      futureCost = (1.0f-futureCostDelay)*futureCost + futureCostDelay*oldFutureCost;
      f.setState(this, futureCost);
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
