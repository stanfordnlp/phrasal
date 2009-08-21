package mt.decoder.efeat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 *  XXX - in progress
 * @author danielcer
 *
 */
public class PhraseBreakEffectiveEntropy implements IncrementalFeaturizer<IString, String> {
	IString[] possibleNextTargetWords = new IString[0];
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
		Set<IString> possibleNextTargetWords = new HashSet<IString>();
		for (ConcreteTranslationOption<IString> opt : options) {
			possibleNextTargetWords.add(opt.abstractOption.translation.get(0));
		}
		
		this.possibleNextTargetWords = possibleNextTargetWords.toArray(this.possibleNextTargetWords);
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) { return null; }

	public void reset() { }
}
