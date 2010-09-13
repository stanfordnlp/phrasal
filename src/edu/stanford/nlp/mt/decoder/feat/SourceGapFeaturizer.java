package edu.stanford.nlp.mt.decoder.feat;

import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.DTUTable;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.train.DTUFeatureExtractor;

/**
 * 
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class SourceGapFeaturizer implements IncrementalFeaturizer<IString, String>, IsolatedPhraseFeaturizer<IString,String> {

	public static final String DEBUG_PROPERTY = "DebugGapCountFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  public static final String CROSSING_FEATURE_NAME = "SG:CrossingCount"; // number of DTU's that have gaps
	public static final String GC_FEATURE_NAME = "SG:GapCount"; // number of DTU's that have gaps
	public static final String GC2_FEATURE_NAME = "SG:Gap2Count"; // number of DTU's that have >= 2 gaps
  public static final String GC3_FEATURE_NAME = "SG:Gap3Count"; // number of DTU's that have >= 3 gaps
  public static final String GC4_FEATURE_NAME = "SG:Gap4Count"; // number of DTU's that have >= 4 gaps
  public static final String GAP_SIZE_FEATURE_NAME = "SG:GapSizeLProb"; // log-probability of gap size

	public String gcFeatureName, gc2FeatureName, gc3FeatureName, gc4FeatureName, crossingFeatureName, gapSizeFeatureName;

  public int nFeatures = 2;

  public double
    gcOnValue, gcOffValue,
    gc2OnValue, gc2OffValue,
    gc3OnValue, gc3OffValue,
    gc4OnValue, gc4OffValue,
    crossingOnValue;

  boolean addGapSizeProb = false;

  public SourceGapFeaturizer() {
		gcFeatureName = GC_FEATURE_NAME;
		gc2FeatureName = GC2_FEATURE_NAME;
    gc3FeatureName = GC3_FEATURE_NAME;
    gc4FeatureName = GC4_FEATURE_NAME;
    crossingFeatureName = CROSSING_FEATURE_NAME;
    gapSizeFeatureName = GAP_SIZE_FEATURE_NAME;
    gcOnValue = 0.0; gcOffValue = 1.0; // Defined like this for historical reasons
    crossingOnValue = -1.0;
	}

  public SourceGapFeaturizer(String... args) {
    this();
    for (String arg : args) {
      String[] els = arg.split(":");
      String name = els[0];
      assert (els.length <= 3);
      if (name.equals("GapSizeLProb")) {
        addGapSizeProb = true;
        ++nFeatures;
      } else if (name.equals("GapCount")) {
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
	public FeatureValue<String> phraseFeaturize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(nFeatures);
    int gapCount = getGapCount(f);
    addStaticFeatures(f, gapCount, list);
    addDynamicFeatures(f, gapCount, list);
    return list;
  }

  @Override
	public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    return null;
    /*
    // Note: appears to hurt performance.
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(nFeatures);
    int gapCount = getGapCount(f);
    addStaticFeatures(f, gapCount, list);
    return list;
    */
	}

  private static int getGapCount(Featurizable<IString,String> f) {
    int gapCount = 0;
    for (IString w : f.foreignPhrase) {
      if (w.id == DTUTable.GAP_STR.id)
        ++gapCount;
    }
    return gapCount;
  }

  private void addStaticFeatures(Featurizable<IString,String> f, int gapCount, List<FeatureValue<String>> list) {
    double gcV = gapCount >= 1 ? gcOnValue : gcOffValue;
    double gc2V = gapCount >= 2 ? gc2OnValue : gc2OffValue;
    double gc3V = gapCount >= 3 ? gc3OnValue : gc3OffValue;
    double gc4V = gapCount >= 4 ? gc4OnValue : gc4OffValue;
    if (gcV != 0.0) list.add(new FeatureValue<String>(gcFeatureName, gcV));
    if(gc2V != 0.0) list.add(new FeatureValue<String>(gc2FeatureName, gc2V));
    if(gc3V != 0.0) list.add(new FeatureValue<String>(gc3FeatureName, gc3V));
    if(gc4V != 0.0) list.add(new FeatureValue<String>(gc4FeatureName, gc4V));
  }

  private void addDynamicFeatures(Featurizable<IString,String> f, int gapCount, List<FeatureValue<String>> list) {

    // Gap size feature:
    if (addGapSizeProb && gapCount >= 1) {
      CoverageSet cs = f.hyp.translationOpt.foreignCoverage;
      List<Integer> binIds = DTUFeatureExtractor.getBins(cs);
      if (gapCount != binIds.size()) {
        System.err.printf("Error: gapCount = %d, binIds = %d, phrase = {%s}, input = {%s}, fc = {%s}\n", gapCount, binIds.size(), f.foreignPhrase, f.foreignSentence, cs);
        throw new RuntimeException();
      }
      double totalGapLogProb = 0.0;
      for (int i=0; i<binIds.size(); ++i) {
        int id = f.hyp.translationOpt.abstractOption.id;
        double gapLogProb = DTUTable.getSourceGapScore(id, i, binIds.get(i));
        totalGapLogProb += gapLogProb;
      }
      list.add(new FeatureValue<String>(gapSizeFeatureName, totalGapLogProb));
    }

    // Crossing feature:
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
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	@Override
  public void reset() { }
}
