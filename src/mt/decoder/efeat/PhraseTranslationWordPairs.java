package mt.decoder.efeat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import edu.stanford.nlp.util.IString;
import mt.base.Sequence;
import mt.base.TranslationOption;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * 
 * @author danielcer
 *
 */
public class PhraseTranslationWordPairs implements IncrementalFeaturizer<IString, String>,  IsolatedPhraseFeaturizer<IString,String> {

	public static String FEATURE_PREFIX = "PTWP";
	final Map<TranslationOption<IString>, List<FeatureValue<String>>> featureCache = new HashMap<TranslationOption<IString>, List<FeatureValue<String>>>();
	
@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) { } 

	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }


	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
			return getFeatureList(f.option.abstractOption);
  }
	
	public List<FeatureValue<String>> getFeatureList(TranslationOption<IString> opt) {
		
		List<FeatureValue<String>> blist = featureCache.get(opt);
		
		if (blist != null) return blist;
		
		blist = new LinkedList<FeatureValue<String>>();
		
		for (IString srcWord : opt.foreign) {
			for (IString trgWord : opt.translation) {
				blist.add(new FeatureValue<String>(FEATURE_PREFIX + ":" + srcWord + "=>" + trgWord, 1.0));
			}
		}
		
		featureCache.put(opt, blist);
		return blist;
	}

	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
		return featurize(f);
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<IString, String> f) {
		return listFeaturize(f);
	}

	public void reset() {
		featureCache.clear();
	}
}
