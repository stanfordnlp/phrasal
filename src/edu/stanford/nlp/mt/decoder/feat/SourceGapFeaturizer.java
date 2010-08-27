package edu.stanford.nlp.mt.decoder.feat;

import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.train.DTUPhraseExtractor;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;

/**
 * 
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class SourceGapFeaturizer implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugGapCountFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  public static final String CROSSING_FEATURE_NAME = "SG:CrossingCount"; // number of DTU's that have gaps
	public static final String GC_FEATURE_NAME = "SG:GapCount"; // number of DTU's that have gaps
	public static final String GC2_FEATURE_NAME = "SG:Gap2Count"; // number of DTU's that have >= 2 gaps
  public static final String GC3_FEATURE_NAME = "SG:Gap3Count"; // number of DTU's that have >= 3 gaps
  public static final String GC4_FEATURE_NAME = "SG:Gap4Count"; // number of DTU's that have >= 4 gaps

	public String gcFeatureName, gc2FeatureName, gc3FeatureName, gc4FeatureName, crossingFeatureName;

  public int nFeatures = 2;

  public double
    gcOnValue, gcOffValue,
    gc2OnValue, gc2OffValue,
    gc3OnValue, gc3OffValue,
    gc4OnValue, gc4OffValue,
    crossingOnValue;

  public SourceGapFeaturizer() {
		gcFeatureName = GC_FEATURE_NAME;
		gc2FeatureName = GC2_FEATURE_NAME;
    gc3FeatureName = GC3_FEATURE_NAME;
    gc4FeatureName = GC4_FEATURE_NAME;
    crossingFeatureName = CROSSING_FEATURE_NAME;
    gcOnValue = 0.0; gcOffValue = 1.0; // Defined like this for historical reasons
    crossingOnValue = -1.0;
	}

  public SourceGapFeaturizer(String... args) {
    this();
    for (String arg : args) {
      String[] els = arg.split(":");
      String name = els[0];
      assert(els.length == 2 || els.length == 3);
      if (name.equals("GapCount")) {
        gcOnValue = Double.parseDouble(els[1]);
        gcOffValue = Double.parseDouble(els[2]);
      } else if (name.equals("Gap2Count")) {
        gc2OnValue = Double.parseDouble(els[1]);
        gc2OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals("Gap3Count")) {
        gc3OnValue = Double.parseDouble(els[1]);
        gc3OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals("Gap4Count")) {
        gc4OnValue = Double.parseDouble(els[1]);
        gc4OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals("CrossingCount")) {
        crossingOnValue = Double.parseDouble(els[1]);
      } else {
        throw new UnsupportedOperationException("Unknown feature: "+name);
      }
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {

    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(nFeatures);

    int gapCount = 0;
    for (IString w : f.foreignPhrase) {
      if (w.id == DTUPhraseExtractor.GAP_STR.id)
        ++gapCount;
    }

    double gcV = gapCount >= 1 ? gcOnValue : gcOffValue;
    double gc2V = gapCount >= 2 ? gc2OnValue : gc2OffValue;
    double gc3V = gapCount >= 3 ? gc3OnValue : gc3OffValue;
    double gc4V = gapCount >= 4 ? gc4OnValue : gc4OffValue;
    if (gcV != 0.0) list.add(new FeatureValue<String>(gcFeatureName, gcV));
    if(gc2V != 0.0) list.add(new FeatureValue<String>(gc2FeatureName, gc2V));
    if(gc3V != 0.0) list.add(new FeatureValue<String>(gc3FeatureName, gc3V));
    if(gc4V != 0.0) list.add(new FeatureValue<String>(gc4FeatureName, gc4V));

    if (crossingOnValue != 0.0 && gapCount >= 1) {

      CoverageSet phraseCS = f.hyp.translationOpt.foreignCoverage; // e.g. .x...x...
      CoverageSet hypCS = f.hyp.foreignCoverage;                   // e.g. xxx..xx..

      int phraseStartIdx = phraseCS.nextSetBit(0);
      int phraseEndIdx = phraseCS.length();

      BitSet middleCS = new BitSet(phraseEndIdx-1);                // e.g. ..x......
      middleCS.set(phraseStartIdx+1,phraseEndIdx-1);
      middleCS.and(hypCS);
      Featurizable<IString,String> curF = f;

      int crossings = 0;
      while (middleCS.cardinality() > 0) {
        boolean inside=false, outside=false;
        CoverageSet curCS = curF.hyp.translationOpt.foreignCoverage;
        int idx = -1;
        while (true) {
          idx = curCS.nextSetBit(idx+1);
          if (idx < 0)
            break;
          if (idx < phraseStartIdx || idx >= phraseEndIdx)
            outside = true;
          else
            inside = true;
          middleCS.clear(idx);
        }
        if (inside && outside)
          ++crossings;
        curF = curF.prior;
      }
      if (crossings > 0)
        list.add(new FeatureValue<String>(crossingFeatureName, crossings*crossingOnValue));
    }

    return list;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	@Override
  public void reset() { }
}
