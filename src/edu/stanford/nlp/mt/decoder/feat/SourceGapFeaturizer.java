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
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.train.DTUFeatureExtractor;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author Michel Galley
 */
public class SourceGapFeaturizer implements
    DerivationFeaturizer<IString, String>,
    RuleFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugGapCountFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String PREFIX = "SG:";
  public static final String DEFAULT_CROSSING_FEATURE_NAME = "CrossingCount"; // number
                                                                              // of
                                                                              // DTU's
                                                                              // that
                                                                              // have
                                                                              // gaps
  public static final String DEFAULT_GC_FEATURE_NAME = "GapCount"; // number of
                                                                   // DTU's that
                                                                   // have gaps
  public static final String DEFAULT_GC2_FEATURE_NAME = "Gap2Count"; // number
                                                                     // of DTU's
                                                                     // that
                                                                     // have >=
                                                                     // 2 gaps
  public static final String DEFAULT_GC3_FEATURE_NAME = "Gap3Count"; // number
                                                                     // of DTU's
                                                                     // that
                                                                     // have >=
                                                                     // 3 gaps
  public static final String DEFAULT_GC4_FEATURE_NAME = "Gap4Count"; // number
                                                                     // of DTU's
                                                                     // that
                                                                     // have >=
                                                                     // 4 gaps
  public static final String DEFAULT_GAP_SIZE_FEATURE_NAME = "GapSizeLProb"; // log-probability
                                                                             // of
                                                                             // gap
                                                                             // size
  public static final String DEFAULT_GAP_SIZE_FEATURE_BIN_NAME = "GapSizeLProbPerBin"; // log-probability
                                                                                       // of
                                                                                       // gap
                                                                                       // size
                                                                                       // (one
                                                                                       // feature
                                                                                       // for
                                                                                       // each
                                                                                       // bin)

  public String gcFeatureName, gc2FeatureName, gc3FeatureName, gc4FeatureName,
      crossingFeatureName, gapSizeFeatureName, gapSizeFeaturePerBinName;

  public int nFeatures = 2;

  public double gcOnValue, gcOffValue, gc2OnValue, gc2OffValue, gc3OnValue,
      gc3OffValue, gc4OnValue, gc4OffValue, crossingOnValue;

  boolean addGapSizeProb = false, featureForEachBin = false;

  public SourceGapFeaturizer() {
    gcFeatureName = PREFIX + DEFAULT_GC_FEATURE_NAME;
    gc2FeatureName = PREFIX + DEFAULT_GC2_FEATURE_NAME;
    gc3FeatureName = PREFIX + DEFAULT_GC3_FEATURE_NAME;
    gc4FeatureName = PREFIX + DEFAULT_GC4_FEATURE_NAME;
    crossingFeatureName = PREFIX + DEFAULT_CROSSING_FEATURE_NAME;
    gapSizeFeatureName = PREFIX + DEFAULT_GAP_SIZE_FEATURE_NAME;
    gapSizeFeaturePerBinName = PREFIX + DEFAULT_GAP_SIZE_FEATURE_BIN_NAME;
    gcOnValue = 0.0;
    gcOffValue = 1.0; // Defined like this for historical reasons
    crossingOnValue = -1.0;
  }

  public SourceGapFeaturizer(String... args) {
    this();
    for (String arg : args) {
      String[] els = arg.split(":");
      String name = els[0];
      assert (els.length <= 3);
      if (name.startsWith(DEFAULT_GAP_SIZE_FEATURE_NAME)) {
        if (name.equals(DEFAULT_GAP_SIZE_FEATURE_BIN_NAME))
          featureForEachBin = true;
        addGapSizeProb = true;
        ++nFeatures;
      } else if (name.equals(DEFAULT_GC_FEATURE_NAME)) {
        gcOnValue = Double.parseDouble(els[1]);
        gcOffValue = Double.parseDouble(els[2]);
      } else if (name.equals(DEFAULT_GC2_FEATURE_NAME)) {
        gc2OnValue = Double.parseDouble(els[1]);
        gc2OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals(DEFAULT_GC3_FEATURE_NAME)) {
        gc3OnValue = Double.parseDouble(els[1]);
        gc3OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals(DEFAULT_GC4_FEATURE_NAME)) {
        gc4OnValue = Double.parseDouble(els[1]);
        gc4OffValue = Double.parseDouble(els[2]);
        ++nFeatures;
      } else if (name.equals(DEFAULT_CROSSING_FEATURE_NAME)) {
        crossingOnValue = Double.parseDouble(els[1]);
      } else {
        throw new UnsupportedOperationException("Unknown feature: " + name);
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(
        nFeatures);
    int gapCount = getGapCount(f);
    addStaticFeatures(f, gapCount, list);
    addDynamicFeatures(f, gapCount, list);
    return list;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    return null;
    /*
     * // Note: appears to hurt performance. List<FeatureValue<String>> list =
     * new ArrayList<FeatureValue<String>>(nFeatures); int gapCount =
     * getGapCount(f); addStaticFeatures(f, gapCount, list); return list;
     */
  }

  private static int getGapCount(Featurizable<IString, String> f) {
    int gapCount = 0;
    for (IString w : f.sourcePhrase) {
      if (w.id == DTUTable.GAP_STR.id)
        ++gapCount;
    }
    return gapCount;
  }

  private void addStaticFeatures(Featurizable<IString, String> f, int gapCount,
      List<FeatureValue<String>> list) {
    double gcV = gapCount >= 1 ? gcOnValue : gcOffValue;
    double gc2V = gapCount >= 2 ? gc2OnValue : gc2OffValue;
    double gc3V = gapCount >= 3 ? gc3OnValue : gc3OffValue;
    double gc4V = gapCount >= 4 ? gc4OnValue : gc4OffValue;
    if (gcV != 0.0)
      list.add(new FeatureValue<String>(gcFeatureName, gcV));
    if (gc2V != 0.0)
      list.add(new FeatureValue<String>(gc2FeatureName, gc2V));
    if (gc3V != 0.0)
      list.add(new FeatureValue<String>(gc3FeatureName, gc3V));
    if (gc4V != 0.0)
      list.add(new FeatureValue<String>(gc4FeatureName, gc4V));
  }

  private void addDynamicFeatures(Featurizable<IString, String> f,
      int gapCount, List<FeatureValue<String>> list) {

    // Gap size feature:
    if (DTUTable.MIN_GAP_SIZE > 0 && addGapSizeProb && gapCount >= 1) {
      CoverageSet cs = f.derivation.rule.sourceCoverage;
      List<Integer> binIds = DTUFeatureExtractor.getBins(cs);
      if (gapCount != binIds.size()) {
        System.err
            .printf(
                "Error: gapCount = %d, binIds = %d, phrase = {%s}, input = {%s}, fc = {%s}\n",
                gapCount, binIds.size(), f.sourcePhrase, f.sourceSentence, cs);
        throw new RuntimeException();
      }
      if (featureForEachBin) {
        for (int i = 0; i < binIds.size(); ++i) {
          int phraseId = f.rule.abstractRule.id;
          int binId = binIds.get(i);
          double gapLogProb = DTUTable.getSourceGapScore(phraseId, i,
              binIds.get(i));
          list.add(new FeatureValue<String>(gapSizeFeaturePerBinName + ":"
              + binId, gapLogProb));
        }
      } else {
        double totalGapLogProb = 0.0;
        for (int i = 0; i < binIds.size(); ++i) {
          int id = f.rule.abstractRule.id;
          double gapLogProb = DTUTable.getSourceGapScore(id, i, binIds.get(i));
          totalGapLogProb += gapLogProb;
        }
        list.add(new FeatureValue<String>(gapSizeFeatureName, totalGapLogProb));
      }
    }

    // Crossing feature:
    if (crossingOnValue != 0.0 && gapCount >= 1) {

      CoverageSet phraseCS = f.derivation.rule.sourceCoverage; // e.g.
                                                                   // .x...x...
      CoverageSet hypCS = f.derivation.sourceCoverage; // e.g. xxx..xx..

      int phraseStartIdx = phraseCS.nextSetBit(0);
      int phraseEndIdx = phraseCS.length();

      BitSet middleCS = new BitSet(phraseEndIdx - 1); // e.g. ..x......
      middleCS.set(phraseStartIdx + 1, phraseEndIdx - 1);
      middleCS.and(hypCS);
      Featurizable<IString, String> curF = f;

      int crossings = 0;
      while (middleCS.cardinality() > 0) {
        boolean inside = false, outside = false;
        CoverageSet curCS = curF.derivation.rule.sourceCoverage;
        int idx = -1;
        while (true) {
          idx = curCS.nextSetBit(idx + 1);
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
        list.add(new FeatureValue<String>(crossingFeatureName, crossings
            * crossingOnValue));
    }
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
