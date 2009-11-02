package mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.train.DTUPhraseExtractor;

/**
 * 
 * @author Michel Galley
 */
public class DTULinearDistortionFeaturizer implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugDTULinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String FEATURE_NAME = "LinearDistortion";
  public static final String GAP_FEATURE_NAME = "GapLinearDistortion";

  public DTULinearDistortionFeaturizer() {
    // Disable "standard" LinearDistortion (hack):
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(2);
    int span = f.option.foreignCoverage.length()-f.option.foreignCoverage.nextSetBit(0);
    int totalSz = 0;
    for(IString fw : f.foreignPhrase)
      if(fw.id != DTUPhraseExtractor.GAP_STR.id)
        ++totalSz;
    int gapSz = span-totalSz;
    list.add(new FeatureValue<String>(GAP_FEATURE_NAME, -1.0*gapSz));
    list.add(new FeatureValue<String>(FEATURE_NAME, -1.0*f.linearDistortion));
    return list;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
