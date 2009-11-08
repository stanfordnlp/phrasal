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

/**
 * 
 * @author Michel Galley
 */
public class GapCountFeaturizer implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugGapCountFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String GC_FEATURE_NAME = "GapCount"; // number of DTU's that have gaps
	public static final String GC2_FEATURE_NAME = "TwoGapCount"; // number of DTU's that have >= 2 gaps

	public String gcFeatureName;
	public String gc2FeatureName;

  public double gcOnValue, gcOffValue, gc2OnValue, gc2OffValue;

  public GapCountFeaturizer() {
		gcFeatureName = GC_FEATURE_NAME;
    gcOnValue = -1.0;
    gcOffValue = 0.0;
		gc2FeatureName = null;
    gc2OnValue = -1.0;
    gc2OffValue = 0.0;
	}

  public GapCountFeaturizer(String... args) {
    this();
    assert(args.length == 1 || args.length == 2);
    if(args[0].contains(":")) {
      String[] els = args[0].split(":");
      assert(els.length == 3);
      gcFeatureName = els[0];
      gcOnValue = Double.parseDouble(els[1]);
      gcOffValue = Double.parseDouble(els[2]);
    } else {
      gcFeatureName = args[0];
    }
    if(args.length == 2) {
      if(args[0].contains(":")) {
        String[] els = args[1].split(":");
        assert(els.length == 3);
        gc2FeatureName = els[0];
        gc2OnValue = Double.parseDouble(els[1]);
        gc2OffValue = Double.parseDouble(els[2]);
      }
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(2);
    int gapCount = 0;
    for(IString w : f.foreignPhrase) {
      if(w.id == DTUPhraseExtractor.GAP_STR.id)
        ++gapCount;
    }
    list.add(new FeatureValue<String>(gcFeatureName, gapCount > 0 ? gcOnValue : gcOffValue));
    if(gc2FeatureName != null)
      list.add(new FeatureValue<String>(gc2FeatureName, gapCount > 1 ? gc2OnValue : gc2OffValue));
    return list;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
