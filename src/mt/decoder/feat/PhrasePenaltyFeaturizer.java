package mt.decoder.feat;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;

/**
 * Moses phrase penalty generated here so that you don't have to read it countless times 
 * from the phrase table.
 * 
 * @author Michel Galley
 *
 * @param <TK>
 */
public class PhrasePenaltyFeaturizer<TK> implements IncrementalFeaturizer<TK,String>, QuickIsolatedPhraseFeaturizer<TK, String>  {
	static public String FEATURE_NAME = "TM:phrasePenalty";
  // mg2008: please don't change to "= 1" since not exactly the same value:
  static private final double MOSES_PHRASE_PENALTY = Math.log(2.718);

	@Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {
		return new FeatureValue<String>(FEATURE_NAME, MOSES_PHRASE_PENALTY); 
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
		return null;
	}

	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<TK, String> f) {
		return featurize(f);
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<TK, String> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
	}

	public void reset() { }
}
