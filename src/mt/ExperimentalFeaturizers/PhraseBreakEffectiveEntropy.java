package mt.ExperimentalFeaturizers;

import java.util.*;
import mt.*;

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
