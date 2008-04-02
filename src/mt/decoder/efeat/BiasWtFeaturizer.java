package mt.decoder.efeat;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 * This feature does *nothing* w.r.t. decoder inference as it is active for all 
 * possible translations. However, it can be used to modulate learning.
 * 
 * @author danielcer
 *
 */
public class BiasWtFeaturizer implements IncrementalFeaturizer<IString, String> {
	static final String FEATURE_NAME = "BiasWt";
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) {
		if (f.done) {
			return new FeatureValue<String>(FEATURE_NAME, f.foreignSentence.size());
		}
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {}

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) { return null; }

	public void reset() { }	
}
