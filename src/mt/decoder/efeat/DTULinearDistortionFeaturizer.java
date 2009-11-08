package mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;

import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.train.DTUPhraseExtractor;
import mt.base.ConcreteTranslationOption;
import mt.base.ConcreteTranslationOption.LinearDistortionType;

/**
 * 
 * @author Michel Galley
 */
public class DTULinearDistortionFeaturizer implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugDTULinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String FEATURE_NAME = "LinearDistortion";
  public static final String GAP_FEATURE_NAME = "GapLinearDistortion";

  private final LinearDistortionType[] featureTypes;
  private final String[] featureNames;

  public DTULinearDistortionFeaturizer() {
    // Disable "standard" LinearDistortion (hack):
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    featureTypes = new LinearDistortionType[0];
    featureNames = new String[0];
  }

  public DTULinearDistortionFeaturizer(String... args) {
    // Disable "standard" LinearDistortion (hack):
    mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    featureTypes = new LinearDistortionType[args.length];
    featureNames = new String[args.length];
    for (int i=0; i<args.length; ++i) {
      featureTypes[i] = LinearDistortionType.valueOf(args[i]);
      featureNames[i] = FEATURE_NAME+":"+featureTypes[i].name();
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {

    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(featureTypes.length+1);
    int span = f.option.foreignCoverage.length()-f.option.foreignCoverage.nextSetBit(0);
    int totalSz = 0;
    for(IString fw : f.foreignPhrase)
      if(fw.id != DTUPhraseExtractor.GAP_STR.id)
        ++totalSz;
    int gapSz = span-totalSz;
    
    list.add(new FeatureValue<String>(GAP_FEATURE_NAME, -1.0*gapSz));

    if(featureTypes.length == 0) {
      list.add(new FeatureValue<String>(FEATURE_NAME, -1.0*f.linearDistortion));
    } else {
      ConcreteTranslationOption<IString>
        priorOpt = (f.prior != null) ? f.prior.option : null,
        currentOpt = f.option;
      for (int i=0; i<featureTypes.length; ++i) {
        int linearDistortion = (priorOpt == null) ? f.option.foreignPos : priorOpt.linearDistortion(currentOpt, featureTypes[i]);
        list.add(new FeatureValue<String>(featureNames[i], -1.0*linearDistortion));
      }
    }

    return list;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
