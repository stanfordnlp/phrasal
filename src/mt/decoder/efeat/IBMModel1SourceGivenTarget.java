package mt.decoder.efeat;

import java.io.IOException;
import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FastFeaturizableHash;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IBMModel1;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 * 
 * @author danielcer
 *
 */
public class IBMModel1SourceGivenTarget implements IncrementalFeaturizer<IString, String> {
	public static String FEATURE_NAME = "IBM1SGT";
	FastFeaturizableHash<IBMModel1.PartialSourceFeatureState> h;
	IBMModel1.PartialSourceFeatureState basePSFS; 
	final IBMModel1 ibmModel1;
	
	public IBMModel1SourceGivenTarget(String filenameSourceTargetModel) throws IOException {
		ibmModel1 = IBMModel1.load(filenameSourceTargetModel);
	}
	
	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) {
		IBMModel1.PartialSourceFeatureState psfs = h.get(f.prior);
		if (psfs == null) psfs = basePSFS;

		for (IString targetWord : f.translatedPhrase) {
			psfs = psfs.appendSourceWord(targetWord);
		}
		h.put(f, psfs);
		
		return new FeatureValue<String>(FEATURE_NAME, psfs.score());
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
		h = new FastFeaturizableHash<IBMModel1.PartialSourceFeatureState>();
		basePSFS = ibmModel1.partialSourceFeatureState(foreign);
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) { return null; }
	
	public void reset() { }
}
