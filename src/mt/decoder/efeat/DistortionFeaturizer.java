package mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.StatefulFeaturizer;
import mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Function;

/**
 * @author Michel Galley
 */
public class DistortionFeaturizer extends StatefulFeaturizer<IString, String> implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugStatefulLinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  // Purposedely the same name as in mt.decoder.feat.LinearDistortionFeaturizer:
  public static final String L_FEATURE_NAME = "LinearDistortion";
  public static final String Q_FEATURE_NAME = "QuadraticDistortion";
  public static final String S_FEATURE_NAME = "StepDistortion";

  public final float futureCostDelay;
  public static final float DEFAULT_FUTURE_COST_DELAY = 0.75f;

  public final int dlimit;
  public static final int DEFAULT_MAX_DISTORTION = 6;

  public Function<Integer, Double>  currentLinearDistortionCost, currentQuadraticDistortionCost, currentStepDistortionCost;
  public Function<Integer, Float>  futureLinearDistortionCost, futureQuadraticDistortionCost, futureStepDistortionCost;

  public DistortionFeaturizer() {
    // Disable "standard" LinearDistortion:
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    dlimit = DEFAULT_MAX_DISTORTION;
    initFunctions();
  }

  public DistortionFeaturizer(String... args) {
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    assert(args.length == 2);
    // First argument determines how much future cost to pay upfront:
    // 0.0 => nothing, as in Moses; 1.0 => everything
    // Second argument determines the "soft" distortion limit:
    futureCostDelay = 1.0f - Float.parseFloat(args[0]);
    assert(futureCostDelay >= 0.0);
    assert(futureCostDelay <= 1.0);
    dlimit = Integer.parseInt(args[1]);
    initFunctions();
  }

  private void initFunctions() {
    currentLinearDistortionCost = new CurrentLinearDistortionCost();
    currentQuadraticDistortionCost = new CurrentQuadraticDistortionCost();
    currentStepDistortionCost = new CurrentStepDistortionCost();
    futureLinearDistortionCost = new FutureLinearDistortionCost();
    futureQuadraticDistortionCost = new FutureQuadraticDistortionCost();
    futureStepDistortionCost = new FutureStepDistortionCost();
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

  private FeatureValue<String> internalFeaturize(String featureName, Featurizable<IString,String> f, Function<Integer,Double> currentCostF, Function<Integer,Float> futureCostF) {
    float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;
    double futureCost;
    if(f.done) {
      futureCost = 0.0f;
    } else {
      int firstGapIndex = f.hyp.foreignCoverage.nextClearBit(0);
      int distance = f.foreignPosition-firstGapIndex;
      futureCost = futureCostF.apply(distance >= 0 ? distance : 0);
      futureCost = (1.0f-futureCostDelay)*futureCost + futureCostDelay*oldFutureCost;
      f.setState(this, (float) futureCost);
    }
    double deltaCost = futureCost - oldFutureCost;
    return new FeatureValue<String>(featureName, currentCostF.apply(f.linearDistortion)+deltaCost);
	}

  class CurrentLinearDistortionCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return -1.0*linearDistortion;
    }
  }

 class CurrentQuadraticDistortionCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return -1.0*linearDistortion*linearDistortion;
    }
  }

  class CurrentStepDistortionCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return linearDistortion > dlimit ? -1.0 : 0.0;
    }
  }

  class FutureLinearDistortionCost implements Function<Integer,Float> {
    public Float apply(Integer distance) {
      if(distance == 0)
        return 0.0f;
      return -1.0f*(1+2*distance);
    }
  }

  class FutureQuadraticDistortionCost implements Function<Integer,Float> {
    public Float apply(Integer distance) {
      if(distance == 0)
        return 0.0f;
      float distancep1 = 1+distance;
      return -1.0f*(distance*distance + distancep1*distancep1); // TODO: better estimate
    }
  }

  class FutureStepDistortionCost implements Function<Integer,Float> { // TODO: check
    public Float apply(Integer distance) {
      if(distance < dlimit)
        return 0.0f;
      if(distance == dlimit) // step backward
        return -1.0f;
      if(distance > dlimit) // step backward and come back
        return -2.0f;
      throw new RuntimeException();
    }
  }

  @Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
		List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(3);
    list.add(internalFeaturize(L_FEATURE_NAME, f, currentLinearDistortionCost, futureLinearDistortionCost));
    list.add(internalFeaturize(Q_FEATURE_NAME, f, currentQuadraticDistortionCost, futureQuadraticDistortionCost));
    list.add(internalFeaturize(S_FEATURE_NAME, f, currentStepDistortionCost, futureStepDistortionCost));
    return list;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
