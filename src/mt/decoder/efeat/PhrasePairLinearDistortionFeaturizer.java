package mt.decoder.efeat;

import java.util.*;

import mt.*;
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 * 
 * @author danielcer
 *
 */
public class PhrasePairLinearDistortionFeaturizer implements IncrementalFeaturizer<IString, String>  {
	public static final String FEATURE_PREFIX = "PLD";
	public static final String ABSOLUTE = ":a";
	public static final String LEFT_SHIFT = ":l";
	public static final String RIGHT_SHIFT = ":r";
	public static final boolean DEFAULT_USE_LRDISTANCE = true;
	public static final boolean DEFAULT_DO_PRIOR = true;
	public static final String CURRENT = ":c";
	public static final String PRIOR = ":p";

	final boolean doPrior;
	final boolean lrDistance;

	public PhrasePairLinearDistortionFeaturizer() {
		lrDistance = DEFAULT_USE_LRDISTANCE;
		doPrior = DEFAULT_DO_PRIOR;
	}
	
	public PhrasePairLinearDistortionFeaturizer(String... args) {
		lrDistance = Boolean.parseBoolean(args[0]);
		doPrior = Boolean.parseBoolean(args[1]);
	}
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) { }

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) {
		List<FeatureValue<String>> fValues = new LinkedList<FeatureValue<String>>();
		
		if (f.linearDistortion == 0) return null;
		for (int i = 0; i < 2; i++) {
			if (i == 1 && !doPrior) break;
		
  		String phrasePair = (i == 0 ? f.foreignPhrase.toString("_") + "=>" + f.translatedPhrase.toString("_") :
  			                    f.prior != null ? f.prior.foreignPhrase.toString("_") + "=>" + f.prior.translatedPhrase.toString("_") : 
  			                    "<s>=><s>");
  		
  		int signedLinearDistortion = (f.prior == null ? -f.foreignPosition : f.prior.hyp.translationOpt.signedLinearDistortion(f.hyp.translationOpt));
  		String pType = (i == 0 ? CURRENT : PRIOR);
  		if (lrDistance) {
  			String type = (signedLinearDistortion < 0 ? LEFT_SHIFT : RIGHT_SHIFT); 
  			fValues.add(new FeatureValue<String>(FEATURE_PREFIX+type+pType+":"+phrasePair, f.linearDistortion));
  		} else {
  			fValues.add(new FeatureValue<String>(FEATURE_PREFIX+ABSOLUTE+pType+":"+phrasePair, f.linearDistortion));
  		}
		}
		
		return fValues; 
	}

	public void reset() { }
}
