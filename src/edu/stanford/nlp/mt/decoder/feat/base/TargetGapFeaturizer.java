package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.DTURule;
import edu.stanford.nlp.mt.pt.DTUTable;
import edu.stanford.nlp.mt.pt.Rule;
import edu.stanford.nlp.mt.train.DTUFeatureExtractor;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class TargetGapFeaturizer extends DerivationFeaturizer<IString,String> implements NeedsCloneable<IString, String>,
    RuleFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  private static final int MINIMUM_GAP_SIZE = 1; // this should reflect the
                                                 // minimum gap size in
                                                 // DTUDecoder

  public static final String PREFIX = "TG:";
  public static final String DEFAULT_GAP_LENGTH_FEATURE_NAME = "Length";
  public static final String DEFAULT_GAP_COUNT_FEATURE_NAME = "GapCount";
  public static final String DEFAULT_CROSSING_FEATURE_NAME = "CrossingCount";
  public static final String DEFAULT_BONBON_FEATURE_NAME = "BonBonCount";
  public static final String DEFAULT_GAP_SIZE_FEATURE_NAME = "GapSizeLProb";
  public static final String DEFAULT_GAP_SIZE_FEATURE_BIN_NAME = "GapSizeLProbPerBin";

  public static boolean WITH_BONBON = false;

  private String gapLengthFeatureName, gapCountFeatureName,
      gapCrossingFeatureName, bonbonFeatureName, gapSizeFeatureName,
      gapSizeFeaturePerBinName;

  boolean addGapSizeProb = false, featureForEachBin = false;

  public TargetGapFeaturizer() {
    gapLengthFeatureName = PREFIX + DEFAULT_GAP_LENGTH_FEATURE_NAME;
    gapCountFeatureName = PREFIX + DEFAULT_GAP_COUNT_FEATURE_NAME;
    gapCrossingFeatureName = PREFIX + DEFAULT_CROSSING_FEATURE_NAME;
    bonbonFeatureName = PREFIX + DEFAULT_BONBON_FEATURE_NAME;
    gapSizeFeatureName = PREFIX + DEFAULT_GAP_SIZE_FEATURE_NAME;
    gapSizeFeaturePerBinName = PREFIX + DEFAULT_GAP_SIZE_FEATURE_BIN_NAME;
  }

  public TargetGapFeaturizer(String... args) {
    this();
    for (String arg : args) {
      String[] els = arg.split(":");
      String name = els[0];
      assert (els.length <= 3);
      if (name.startsWith(DEFAULT_GAP_SIZE_FEATURE_NAME)) {
        addGapSizeProb = true;
        if (name.equals(DEFAULT_GAP_SIZE_FEATURE_BIN_NAME)) {
          featureForEachBin = true;
        }
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {

    if (!(f instanceof DTUFeaturizable))
      return null;
    DTUFeaturizable<IString, String> dtuF = (DTUFeaturizable<IString, String>) f;
    if (!(dtuF.abstractOption instanceof DTURule))
      return null;

    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(3);

    // Find out where we currently are within f.abstractOption:
    int segIdx = f.getSegmentIdx();
    Sequence<IString>[] dtus = ((DTURule<IString>) dtuF.abstractOption).dtus;

    if (segIdx == 0) { // We just started generating a discontinuous phrase:

      // First segment of a target-side dtu: cost will be <= -1.0, so pay -1.0
      // upfront:
      feats.add(new FeatureValue<String>(gapCountFeatureName, -1.0));
      feats.add(new FeatureValue<String>(gapLengthFeatureName, -1.0
          * MINIMUM_GAP_SIZE));

      return feats;

    } else { // We are between second and last element of current discontinuous
             // phrase:

      Featurizable<IString, String> curF = f.prior;

      // Backtrack until we find previous element of the same discontinuous
      // phrase:
      for (int i = 0; curF != null; ++i, curF = curF.prior) {

        if (curF instanceof DTUFeaturizable) {

          if (((DTUFeaturizable<IString, String>) curF).abstractOption == dtuF.abstractOption) {
            // Same discontinuous phrase:
            if (dtuF.abstractOption != null) {

              int curIdx = segIdx - 1;
              int szCurF = dtus[curIdx].size();
              int distance = f.targetPosition - curF.targetPosition
                  - szCurF;

              // f.translationPosition
              // curF.translationPosition
              // 1 2 3 4 5 6 7 8 9
              // x x x x x . . . x x x
              // < curF > < f >

              if (distance <= 0) {
                System.err.println("Warning: target gap has negative size: "
                    + distance);
                return null;
              }

              // Feature value: distance-1, since already paid -1.0:
              if (distance > DTUHypothesis.getMaxTargetPhraseSpan()) {
                // We might be here during nbest list extraction, because
                // recombination can
                // create a longer gap than seen during beam search:
                feats.add(new FeatureValue<String>(gapLengthFeatureName, -100));
              } else {
                int len = distance - MINIMUM_GAP_SIZE;
                if (len != 0)
                  feats.add(new FeatureValue<String>(gapLengthFeatureName, -1.0
                      * len));
                if (addGapSizeProb) {
                  int binId = DTUFeatureExtractor.sizeToBin(distance);
                  int phraseId = f.rule.abstractRule.id;
                  double gapScore = DTUTable.getTargetGapScore(phraseId,
                      curIdx, binId);
                  if (featureForEachBin) {
                    feats.add(new FeatureValue<String>(gapSizeFeaturePerBinName
                        + ":" + binId, gapScore));
                  } else {
                    feats.add(new FeatureValue<String>(gapSizeFeatureName,
                        gapScore));
                  }
                }
              }
              addCrossingCountFeatures(feats,
                  (DTUFeaturizable<IString, String>) curF, dtuF);
              return feats;
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(2);
    int gapCount = getGapCount(f);
    if (gapCount > 0) {
      double score = -1.0 * gapCount;
      list.add(new FeatureValue<String>(gapCountFeatureName, score));
      list.add(new FeatureValue<String>(gapLengthFeatureName, score));
    }
    return list;
  }

  private Map<Rule<IString>, DTUFeaturizable<IString, String>> seenOptions = new HashMap<Rule<IString>, DTUFeaturizable<IString, String>>();

  private void addCrossingCountFeatures(List<FeatureValue<String>> feats,
      DTUFeaturizable<IString, String> startF,
      DTUFeaturizable<IString, String> endF) {

    Featurizable<IString, String> curF = endF.prior;

    seenOptions.clear();

    int bonbonCount = 0;
    for (int i = 0; curF != null && curF != startF; ++i, curF = curF.prior) {
      if (curF instanceof DTUFeaturizable) {
        Rule<IString> curOption = ((DTUFeaturizable<IString, String>) curF).abstractOption;
        if (curOption != endF.abstractOption) {
          // Detect bon-bons:
          if (CoverageSet.cross(curF.rule.sourceCoverage,
              endF.rule.sourceCoverage))
            ++bonbonCount;
          seenOptions.put(curOption, (DTUFeaturizable<IString, String>) curF);
        }
      }
    }

    // Detect target-side cross-serial:
    int crossingCount = 0;
    for (DTUFeaturizable<IString, String> firstF : seenOptions.values()) {
      if (firstF.getSegmentIdx() > 0) {
        ++crossingCount;
      }
    }

    if (WITH_BONBON) {
      if (bonbonCount > 0)
        feats.add(new FeatureValue<String>(bonbonFeatureName, -1.0
            * bonbonCount));
    } else {
      crossingCount += bonbonCount;
    }
    if (crossingCount > 0)
      feats.add(new FeatureValue<String>(gapCrossingFeatureName, -1.0
          * crossingCount));
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    TargetGapFeaturizer f = (TargetGapFeaturizer) super.clone();
    f.seenOptions = new HashMap<Rule<IString>, DTUFeaturizable<IString, String>>();
    return f;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  private static int getGapCount(Featurizable<IString, String> f) {
    Rule<IString> opt = f.rule.abstractRule;
    int sz = 0;
    for (IString el : f.targetPhrase)
      if (el.equals(DTUTable.GAP_STR))
        ++sz;
    return sz;
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
