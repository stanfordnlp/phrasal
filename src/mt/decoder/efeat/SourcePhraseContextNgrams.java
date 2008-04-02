package mt.decoder.efeat;

import java.util.*;

import mt.*;
import mt.base.ARPALanguageModel;
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
import mt.base.InsertedStartEndToken;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * 
 * @author danielcer
 *
 */
public class SourcePhraseContextNgrams implements IncrementalFeaturizer<IString, String>,  IsolatedPhraseFeaturizer<IString,String> {
	public static final String FEATURE_PREFIX = "SrcPhrCxtNG:";
	public static final Type DEFAULT_TYPE = Type.lr; 
	public static final int DEFAULT_N_GRAM_CONTEXT = 3;
	public enum Type {l,r,lr};
	
	final int ngramContext;
	final Type type;
	
	public SourcePhraseContextNgrams(String... args) {
		ngramContext = Integer.parseInt(args[0]);
		type = Type.valueOf(args[1]);
	}
	
	public SourcePhraseContextNgrams() {
		ngramContext = DEFAULT_N_GRAM_CONTEXT;
		type = DEFAULT_TYPE;
	}
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) { }

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) {
		List<FeatureValue<String>> fList = new LinkedList<FeatureValue<String>>();
		String trans = f.foreignPhrase.toString("_")+"=>"+f.translatedPhrase.toString("_");
		Sequence<IString> wrappedSource = new InsertedStartEndToken<IString>(f.foreignSentence, ARPALanguageModel.START_TOKEN, ARPALanguageModel.END_TOKEN);
		int sz = wrappedSource.size();
		int startBoundry = f.foreignPosition+1;
		int endBoundry = f.foreignPosition+f.foreignPhrase.size()+1;
		for (int i = 0; i < ngramContext; i++) {
			int leftStart = startBoundry - i - 1;
			int rightEnd = endBoundry + i + 1;
  		switch (type) {
  		case l:
  			if (leftStart < 0) continue;
  			fList.add(new FeatureValue<String>(FEATURE_PREFIX+"l:"+wrappedSource.subsequence(leftStart, startBoundry).toString("_")+"|"+trans, 1.0));
  			break;
  		case r:
  			if (rightEnd > sz) continue;
  			fList.add(new FeatureValue<String>(FEATURE_PREFIX+"r:"+trans+"|"+wrappedSource.subsequence(endBoundry,rightEnd).toString("_"), 1.0));
  			break;
  		case lr:
  			if (leftStart < 0 || rightEnd > sz) continue;
  			fList.add(new FeatureValue<String>(FEATURE_PREFIX+"lr:"+wrappedSource.subsequence(leftStart, startBoundry).toString("_")+"|"+trans+"|"+wrappedSource.subsequence(endBoundry,rightEnd).toString("_"), 1.0));
  			break;
  		}
		}
		return fList;
	}

	@Override
	public void reset() { }

	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
		return featurize(f);
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<IString, String> f) {
		return listFeaturize(f);
	}

}
