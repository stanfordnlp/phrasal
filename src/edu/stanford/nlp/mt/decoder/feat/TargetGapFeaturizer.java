package edu.stanford.nlp.mt.decoder.feat;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.DTUOption;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class TargetGapFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private static final int MINIMUM_GAP_SIZE = 1; // this should reflect the minimum gap size in DTUDecoder

  public static final String GAP_LENGTH_FEATURE_NAME = "TG:Length";
  public static final String GAP_COUNT_FEATURE_NAME = "TG:GapCount";
  public static final String CROSSING_FEATURE_NAME = "TG:CrossingCount";

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {

    if (!(f instanceof DTUFeaturizable)) return null;
    DTUFeaturizable<TK,String> dtuF = (DTUFeaturizable<TK,String>) f;
    if (!(dtuF.abstractOption instanceof DTUOption)) return null;

    // Find out where we currently are within f.abstractOption:
    int dtuIdx = -1;
    Sequence<TK>[] dtus = ((DTUOption<TK>)dtuF.abstractOption).dtus;
    for (int i=0; i<dtus.length; ++i) {
      assert (f.translatedPhrase.size() > 0);
      if (dtus[i] == f.translatedPhrase) {
        dtuIdx = i;
        break;
      }
    }

    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(3);
    feats.add(new FeatureValue<String>(GAP_COUNT_FEATURE_NAME, -1.0));
    
    if (dtuIdx < 0) {

      // Didn't find current segment, internal error:
      throw new RuntimeException();

    } else if (dtuIdx == 0) { // We just started generating a discontinuous phrase:

      // First segment of a target-side dtu: cost will be <= -1.0, so pay -1.0 upfront:
      feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -1.0*MINIMUM_GAP_SIZE));
      return feats;

    } else { // We are between second and last element of current discontinuous phrase:

      Featurizable<TK,String> curF = f.prior;

      int crossings = 0;

      // Backtrack until we find previous element of the same discontinuous phrase:
      for (int i=0; curF != null; ++i, curF = curF.prior) {
        if (curF instanceof DTUFeaturizable) {

          if (((DTUFeaturizable<TK,String>)curF).abstractOption == dtuF.abstractOption) {
            // Same discontinuous phrase: 
            if (dtuF.abstractOption != null) {

              int distance = f.translationPosition-curF.translationPosition-1;

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
                feats.add(new FeatureValue<String>(GAP_LENGTH_FEATURE_NAME, -1.0*(distance - MINIMUM_GAP_SIZE)));
              }
              if (crossings > 0)
                feats.add(new FeatureValue<String>(CROSSING_FEATURE_NAME, -1.0*crossings));
              return feats;
            }
          } else {
            // Other phrase: detect crossings:

          }
        }
      }
    }
    return null;
  }

  public FeatureValue<String> featurize(Featurizable<TK,String> f) {
    return null;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
	}

	public void reset() { }
}
