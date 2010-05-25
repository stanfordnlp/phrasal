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
    if (args.length == 1) {
      futureCostDelay = Float.parseFloat(args[0]);
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
    if (f.done) {
      futureCost = 0.0f;
    } else {
      futureCost = (1.0f-futureCostDelay)*futureCost(f) + futureCostDelay*oldFutureCost;
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

  public static int futureCost(Featurizable<IString,String> f) {
    int nextWordIndex = f.hyp.translationOpt.foreignCoverage.length();
    int firstGapIndex = f.hyp.foreignCoverage.nextClearBit(0);
    if (firstGapIndex > nextWordIndex)
      firstGapIndex = nextWordIndex;
    int futureCost = nextWordIndex - firstGapIndex;
    // General case:
    // x x . . . x x X . x x x .   // x:covered X:last
    //     6 5 4 3 2 1 cost=6 + 3 + 3 = 12
    //     j           i
    // Special case:
    // [x] [X] [x x] . . .   // x:covered X:`last
    //          x x      2 + fc=6 delta=6
    //  x                4 + fc=2 delta=-4
    //      x            0 + fc=2 delta=0
    //     6 5 4 3 2 1 cost = 3
    //     j           i
    int p = firstGapIndex-1;
    for (;;) {
      p = f.hyp.foreignCoverage.nextSetBit(p+1);
      if (p < 0)
        break;
      ++futureCost;
    }
    return futureCost;
  }

  public static int getEOSDistortion(Featurizable<IString,String> f) {
    if (f.done) {
      int endGap = f.foreignSentence.size() - f.option.foreignCoverage.length();
      //System.err.println("hyp span: "+f.hyp.foreignCoverage);
      //System.err.println("opt span: "+f.option.foreignCoverage);
      //System.err.println("opt text: "+f.option.abstractOption.foreign);
      //System.err.println("len: "+f.option.foreignCoverage.length());
      //System.err.println("size: "+f.foreignSentence.size());
      //System.err.println("endGap: "+endGap);
      assert(endGap >= 0);
      return endGap;
    }
    return 0;
  }
}
