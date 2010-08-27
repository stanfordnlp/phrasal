package edu.stanford.nlp.mt.decoder.feat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.DTUOption;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class TargetGapFeaturizer<TK> implements ClonedFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private static final int MINIMUM_GAP_SIZE = 1; // this should reflect the minimum gap size in DTUDecoder

  public static final String GAP_LENGTH_FEATURE_NAME = "TG:Length";
  public static final String GAP_COUNT_FEATURE_NAME = "TG:GapCount";
  public static final String CROSSING_FEATURE_NAME = "TG:CrossingCount";
  public static final String BONBON_FEATURE_NAME = "TG:BonBonCount";

  private static final boolean WITH_BONBON = false;

  @Override
  @SuppressWarnings("unchecked")
  public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {

    if (!(f instanceof DTUFeaturizable)) return null;
    DTUFeaturizable<TK,String> dtuF = (DTUFeaturizable<TK,String>) f;
    if (!(dtuF.abstractOption instanceof DTUOption)) return null;

    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(3);

    // Find out where we currently are within f.abstractOption:
    int dtuIdx = positionInDTU(f);
    Sequence<TK>[] dtus = ((DTUOption<TK>)dtuF.abstractOption).dtus;

    if (dtuIdx == 0) { // We just started generating a discontinuous phrase:

      // First segment of a target-side dtu: cost will be <= -1.0, so pay -1.0 upfront:
      feats.add(new FeatureValue<String>(GAP_COUNT_FEATURE_NAME, -1.0));
      feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -1.0*MINIMUM_GAP_SIZE));

      return feats;

    } else { // We are between second and last element of current discontinuous phrase:

      Featurizable<TK,String> curF = f.prior;

      // Backtrack until we find previous element of the same discontinuous phrase:
      for (int i=0; curF != null; ++i, curF = curF.prior) {
        
        if (curF instanceof DTUFeaturizable) {

          if (((DTUFeaturizable<TK,String>)curF).abstractOption == dtuF.abstractOption) {
            // Same discontinuous phrase: 
            if (dtuF.abstractOption != null) {

              int curIdx = dtuIdx-1;
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
              addCrossingCountFeatures(feats, (DTUFeaturizable<TK,String>)curF, dtuF);
              return feats;
            }
          }
        }
      }
    }
    return null;
  }

  private Map<TranslationOption<TK>, DTUFeaturizable<TK,String>> seenOptions =
    new HashMap<TranslationOption<TK>, DTUFeaturizable<TK,String>>();

  private void addCrossingCountFeatures(List<FeatureValue<String>> feats, DTUFeaturizable<TK,String> startF, DTUFeaturizable<TK,String> endF) {

    Featurizable<TK,String> curF = endF.prior;

    seenOptions.clear();

    int bonbonCount = 0;
    for (int i=0; curF != null && curF != startF; ++i, curF = curF.prior) {
      if (curF instanceof DTUFeaturizable) {
        TranslationOption<TK> curOption = ((DTUFeaturizable<TK,String>)curF).abstractOption;
        if (curOption != endF.abstractOption) {
          // Detect bon-bons:
          if (CoverageSet.cross(curF.option.foreignCoverage, endF.option.foreignCoverage))
            ++bonbonCount;
          seenOptions.put(curOption, (DTUFeaturizable<TK,String>) curF);
        }
      }
    }

    // Detect target-side cross-serial:
    int crossingCount = 0;
    for (DTUFeaturizable<TK,String> firstF : seenOptions.values()) {
      if (positionInDTU(firstF) > 0) {
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

  private int positionInDTU(Featurizable<TK,String> f) {
    Hypothesis h = f.hyp;
    final int posIdx;
    if (!(h instanceof DTUHypothesis)) {
      posIdx = 0;
    } else {
      DTUHypothesis dtuH = ((DTUHypothesis)f.hyp);
      posIdx = ((DTUFeaturizable)f).segmentIdx;
    }
    //System.err.printf("posIdx = %d in %s ||| %s", posIdx, f.translatedPhrase, f.hyp.translationOpt.abstractOption);
    return posIdx;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    TargetGapFeaturizer f = (TargetGapFeaturizer) super.clone();
    f.seenOptions = new HashMap<TranslationOption<TK>, Featurizable<TK,String>>();
    return f;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<TK,String> f) {
    return null;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
	}

	@Override
  public void reset() { }
}
