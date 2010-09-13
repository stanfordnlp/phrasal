package edu.stanford.nlp.mt.decoder.feat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.DTUOption;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class TargetGapFeaturizer implements ClonedFeaturizer<IString, String>, IsolatedPhraseFeaturizer<IString,String>  {

  public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private static final int MINIMUM_GAP_SIZE = 1; // this should reflect the minimum gap size in DTUDecoder

  public static final String GAP_LENGTH_FEATURE_NAME = "TG:Length";
  public static final String GAP_COUNT_FEATURE_NAME = "TG:GapCount";
  public static final String CROSSING_FEATURE_NAME = "TG:CrossingCount";
  public static final String BONBON_FEATURE_NAME = "TG:BonBonCount";

  private static final boolean WITH_BONBON = false;

  @Override
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {

    if (!(f instanceof DTUFeaturizable)) return null;
    DTUFeaturizable<IString,String> dtuF = (DTUFeaturizable<IString,String>) f;
    if (!(dtuF.abstractOption instanceof DTUOption)) return null;

    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(3);

    // Find out where we currently are within f.abstractOption:
    int segIdx = f.getSegmentIdx();
    Sequence<IString>[] dtus = ((DTUOption<IString>)dtuF.abstractOption).dtus;

    if (segIdx == 0) { // We just started generating a discontinuous phrase:

      // First segment of a target-side dtu: cost will be <= -1.0, so pay -1.0 upfront:
      feats.add(new FeatureValue<String>(GAP_COUNT_FEATURE_NAME, -1.0));
      feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -1.0*MINIMUM_GAP_SIZE));

      return feats;

    } else { // We are between second and last element of current discontinuous phrase:

      Featurizable<IString,String> curF = f.prior;

      // Backtrack until we find previous element of the same discontinuous phrase:
      for (int i=0; curF != null; ++i, curF = curF.prior) {
        
        if (curF instanceof DTUFeaturizable) {

          if (((DTUFeaturizable<IString,String>)curF).abstractOption == dtuF.abstractOption) {
            // Same discontinuous phrase: 
            if (dtuF.abstractOption != null) {

              int curIdx = segIdx-1;
              int szCurF = dtus[curIdx].size();
              int distance = f.translationPosition - curF.translationPosition - szCurF;

              //                 f.translationPosition
              // curF.translationPosition
              // 1 2 3 4 5 6 7 8 9
              // x x x x x . . . x x x
              // <  curF >       < f >

              if (distance <= 0) {
                System.err.println("Warning: target gap has negative size: "+distance);
                return null;
              }

              // Feature value: distance-1, since already paid -1.0:
              if (distance > DTUHypothesis.getMaxTargetPhraseSpan()) {
                // We might be here during nbest list extraction, because recombination can
                // create a longer gap than seen during beam search:
                feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -100));
              } else {
                int len = distance - MINIMUM_GAP_SIZE;
                if (len != 0)
                  feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -1.0*len));
              }
              addCrossingCountFeatures(feats, (DTUFeaturizable<IString,String>)curF, dtuF);
              return feats;
            }
          }
        }
      }
    }
    return null;
  }

  @Override
	public FeatureValue<String> phraseFeaturize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(2);
    int gapCount = getGapCount(f);
    if (gapCount > 0) {
      double score = -1.0 * gapCount;
      list.add(new FeatureValue<String>(GAP_COUNT_FEATURE_NAME, score));
      list.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, score));
    }
    return list;
	}

  private Map<TranslationOption<IString>, DTUFeaturizable<IString,String>> seenOptions =
    new HashMap<TranslationOption<IString>, DTUFeaturizable<IString,String>>();

  private void addCrossingCountFeatures(List<FeatureValue<String>> feats, DTUFeaturizable<IString,String> startF, DTUFeaturizable<IString,String> endF) {

    Featurizable<IString,String> curF = endF.prior;

    seenOptions.clear();

    int bonbonCount = 0;
    for (int i=0; curF != null && curF != startF; ++i, curF = curF.prior) {
      if (curF instanceof DTUFeaturizable) {
        TranslationOption<IString> curOption = ((DTUFeaturizable<IString,String>)curF).abstractOption;
        if (curOption != endF.abstractOption) {
          // Detect bon-bons:
          if (CoverageSet.cross(curF.option.foreignCoverage, endF.option.foreignCoverage))
            ++bonbonCount;
          seenOptions.put(curOption, (DTUFeaturizable<IString,String>) curF);
        }
      }
    }

    // Detect target-side cross-serial:
    int crossingCount = 0;
    for (DTUFeaturizable<IString,String> firstF : seenOptions.values()) {
      if (firstF.getSegmentIdx() > 0) {
        ++crossingCount;
      }
    }

    if (WITH_BONBON) {
      if (bonbonCount > 0)
        feats.add(new FeatureValue<String>(BONBON_FEATURE_NAME, -1.0*bonbonCount));
    } else {
      crossingCount += bonbonCount;
    }
    if (crossingCount > 0)
      feats.add(new FeatureValue<String>(CROSSING_FEATURE_NAME, -1.0*crossingCount));
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    TargetGapFeaturizer f = (TargetGapFeaturizer) super.clone();
    f.seenOptions = new HashMap<TranslationOption<IString>, DTUFeaturizable<IString,String>>();
    return f;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	@Override
  public void reset() { }

  private static int getGapCount(Featurizable<IString,String> f) {
    TranslationOption opt = f.option.abstractOption;
    if (opt instanceof DTUOption) {
      DTUOption dtuOpt = (DTUOption) opt;
      return dtuOpt.dtus.length;
    }
    return 0;
  }

}
