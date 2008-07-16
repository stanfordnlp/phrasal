package mt.decoder.efeat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.IsolatedPhraseFeaturizer;

import edu.stanford.nlp.util.IString;

/**
 * 
 * @author danielcer
 *
 */
public class LRPhraseBoundryFeaturizer implements IncrementalFeaturizer<IString, String>,  IsolatedPhraseFeaturizer<IString,String>  {
	public static final String FEATURE_PREFIX = "LRPB";
	public static final String PREFIX_L = ":l";
	public static final String PREFIX_R = ":r";
	public static final String PREFIX_SRC = ":s";
	public static final String PREFIX_TRG =  ":t";
	
	public static final int DEFAULT_SIZE=2;
	public static final boolean DEFAULT_DO_SOURCE=true;
	public static final boolean DEFAULT_DO_TARGET=true;
	public final boolean doSource;
	public final boolean doTarget;
	public final int size;
	
	public LRPhraseBoundryFeaturizer() {
		size = DEFAULT_SIZE;
		doSource = DEFAULT_DO_SOURCE;
		doTarget = DEFAULT_DO_TARGET;
	}
	
	public LRPhraseBoundryFeaturizer(String... args) {
		size = Integer.parseInt(args[0]);
		if (args.length == 1) {
			doSource = DEFAULT_DO_SOURCE;
			doTarget = DEFAULT_DO_TARGET;
			return;
		}
		doSource = Boolean.parseBoolean(args[1]);
		doTarget = Boolean.parseBoolean(args[2]);
	}
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) { }

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) {
		List<FeatureValue<String>> blist = new LinkedList<FeatureValue<String>>();
		
		if (doSource) {
			int foreignPhraseSz = f.foreignPhrase.size();
			int sourceMax = Math.min(size, foreignPhraseSz);
			for (int i = 0; i < sourceMax; i++) {
				Sequence<IString> sourceB = f.foreignPhrase.subsequence(0, i+1);
				blist.add(new FeatureValue<String>(FEATURE_PREFIX+PREFIX_L+PREFIX_SRC+":"+sourceB.toString("_"), 1.0));
			}
			int sourceMin = Math.max(0, foreignPhraseSz-size);
			
			for (int i = foreignPhraseSz-1; i >= sourceMin; i--) {
				Sequence<IString> sourceB = f.foreignPhrase.subsequence(i,foreignPhraseSz);
				blist.add(new FeatureValue<String>(FEATURE_PREFIX+PREFIX_R+PREFIX_SRC+":"+sourceB.toString("_"), 1.0));
			}
		}
		
		if (doTarget) {
			int translationPhraseSz = f.translatedPhrase.size();
			int targetMax = Math.min(size, translationPhraseSz);
			for (int i = 0; i < targetMax; i++) {
				Sequence<IString> targetB = f.translatedPhrase.subsequence(0, i+1);
				blist.add(new FeatureValue<String>(FEATURE_PREFIX+PREFIX_L+PREFIX_TRG+":"+targetB.toString("_"), 1.0)); 
			}
			
			int targetMin = Math.max(0, translationPhraseSz-size);
			for (int i = translationPhraseSz-1; i >= targetMin; i--) {
				Sequence<IString> targetB = f.translatedPhrase.subsequence(i, translationPhraseSz);
				blist.add(new FeatureValue<String>(FEATURE_PREFIX+PREFIX_R+PREFIX_TRG+":"+targetB.toString("_"), 1.0));
			}
		}
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
	
	public void reset() { }
}
