package mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

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

	public static final String DEBUG_PROPERTY = "DebugDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String L_FEATURE_NAME = "LinearDistortion";
  public static final String P_FEATURE_NAME = "PolynomialDistortion";
  public static final String S_FEATURE_NAME = "StepDistortion";

  public final float futureCostDelay;
  public static final float DEFAULT_FUTURE_COST_DELAY = 0.75f;

  public final int dlimit;
  public static final int DEFAULT_MAX_DISTORTION = 6;

  public final float polyOrder;
  public static final float DEFAULT_POLYNOMIAL_ORDER = 1.5f;

  public Function<Integer, Double> linearDistortionCurrentCost, polynomialDistortionCurrentCost, stepDistortionCurrentCost;
  public Function<Integer, Float> linearDistortionFutureCost, polynomialDistortionFutureCost, stepDistortionFutureCost;

  public static Random random = new Random();

  public DistortionFeaturizer() {
    // Disable "standard" LinearDistortion:
    //mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    dlimit = DEFAULT_MAX_DISTORTION;
    polyOrder = DEFAULT_POLYNOMIAL_ORDER;
    initFunctions();
  }

  public DistortionFeaturizer(String... args) {
    //mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    assert(args.length == 3);
    // First argument determines how much future cost to pay upfront:
    // 0.0 => nothing, as in Moses; 1.0 => everything
    futureCostDelay = 1.0f - Float.parseFloat(args[0]);
    assert(futureCostDelay >= 0.0);
    assert(futureCostDelay <= 1.0);
    // Second argument determines the "soft" distortion limit:
    dlimit = Integer.parseInt(args[1]);
    // Third argument determines order of polynomial:
    polyOrder = Float.parseFloat(args[2]);
    initFunctions();
  }

  private void initFunctions() {
    linearDistortionCurrentCost = new LinearDistortionCurrentCost();
    polynomialDistortionCurrentCost = new PolynomialDistortionCurrentCost();
    stepDistortionCurrentCost = new StepDistortionCurrentCost();
    linearDistortionFutureCost = new LinearDistortionFutureCost();
    polynomialDistortionFutureCost = new PolynomialDistortionFutureCost();
    stepDistortionFutureCost = new StepDistortionFutureCost();
  }

  private FeatureValue<String> internalFeaturize(String featureName, Featurizable<IString,String> f, boolean debug,
                                                 Function<Integer,Double> currentCostF, Function<Integer,Float> futureCostF) {
    float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;
    double futureCost;
    if(f.done) {
      futureCost = 0.0f;
    } else {
      int firstGapIndex = f.hyp.foreignCoverage.nextClearBit(0);
      int distance = f.foreignPosition-firstGapIndex;
      futureCost = futureCostF.apply(distance);

      futureCost = (1.0f-futureCostDelay)*futureCost + futureCostDelay*oldFutureCost;
      f.setState(this, (float) futureCost);
      if(debug) {
        System.err.printf("Debugging efeat.DistortionFeaturizer:\n%s\n%s %d\n%s %d\n%s %d\n%s %s\n%s %f\n%s %f\n",
          f.hyp.toString(),
          "first gap index: ", firstGapIndex,
          "current word index: ", f.foreignPosition,
          "distance: ", distance,
          "feature name: ", featureName,
          "current cost:", futureCostF.apply(distance >= 0 ? distance : 0),
          "future cost:", futureCost
        );
      }
    }
    double deltaCost = futureCost - oldFutureCost;
    return new FeatureValue<String>(featureName, currentCostF.apply(f.linearDistortion)+deltaCost);
	}

  class LinearDistortionCurrentCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return -1.0*linearDistortion;
    }
  }

 class PolynomialDistortionCurrentCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return -1.0*Math.pow(linearDistortion, polyOrder);
    }
  }

  class StepDistortionCurrentCost implements Function<Integer,Double> {
    public Double apply(Integer linearDistortion) {
      return linearDistortion > dlimit ? -1.0 : 0.0;
    }
  }

  class LinearDistortionFutureCost implements Function<Integer,Float> {
    public Float apply(Integer distance) {
      if(distance < 0)
        return 0.0f;
      return -1.0f*(1+2*distance);
    }
  }

  class PolynomialDistortionFutureCost implements Function<Integer,Float> {
    public Float apply(Integer distance) {
      if(distance < 0)
        return 0.0f;
      float distancep1 = 1+distance;
      return -1.0f*(float)(Math.pow(distance, polyOrder) + Math.pow(distancep1, polyOrder)); // TODO: better estimate
    }
  }

  class StepDistortionFutureCost implements Function<Integer,Float> { // TODO: check
    public Float apply(Integer distance) {
      if(distance <= dlimit)
        return 0.0f;
      else if(distance == dlimit+1) // step backward
        return -1.0f;
      else if(distance > dlimit+1) // step backward and come back
        return -2.0f;
      throw new RuntimeException();
    }
  }

  @Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
		List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(3);
    boolean debug = (DEBUG && random.nextInt(1000) == 0);
    list.add(internalFeaturize(L_FEATURE_NAME, f, debug, linearDistortionCurrentCost, linearDistortionFutureCost));
    list.add(internalFeaturize(P_FEATURE_NAME, f, debug, polynomialDistortionCurrentCost, polynomialDistortionFutureCost));
    list.add(internalFeaturize(S_FEATURE_NAME, f, debug, stepDistortionCurrentCost, stepDistortionFutureCost));
    return list;
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

  @Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
