package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.DTUOption;

/**
 * @author Michel Galley
 */
public class TargetGapFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private static final double FUTURE_COST = -1.0;

  public static final String GAP_FEATURE_NAME = "TargetGapLinearDistortion";

  @Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {

    if (!(f instanceof DTUFeaturizable)) return null;
    DTUFeaturizable<TK,String> dtuF = (DTUFeaturizable<TK,String>) f;
    if (!(dtuF.abstractOption instanceof DTUOption)) return null;

    int dtuIdx = -1;
    Sequence<TK>[] dtus = ((DTUOption<TK>)dtuF.abstractOption).dtus;
    for(int i=0; i<dtus.length; ++i) {
      assert(f.translatedPhrase.size() > 0);
      if(dtus[i] == f.translatedPhrase) {
        dtuIdx = i;
        break;
      }
    }
    
    if(dtuIdx < 0) {
      // Didn't find current segment, internal error:
      throw new RuntimeException();
    } else if(dtuIdx == 0) {
      // First segment of a target-side dtu: cost will be <= -1.0, so pay -1.0 upfront:
      return new FeatureValue<String>(GAP_FEATURE_NAME, FUTURE_COST);
    } else {
      Featurizable<TK,String> curF = f.prior;
      // (better option that backtracking on each Featurizable?)
      for (int i=0; curF != null; ++i, curF = curF.prior) {
        if(curF instanceof DTUFeaturizable) {
          if(((DTUFeaturizable<TK,String>)curF).abstractOption == dtuF.abstractOption) {
            if(dtuF.abstractOption != null) {
              int distance = f.translationPosition-curF.translationPosition-1;
              if(distance <= 0) {
                System.err.println("WARNING: size of target gap is wrong: "+distance);
                return null;
              }
              // Feature value: distance-1, since already paid -1.0:
              if(distance > DTUHypothesis.getMaxTargetPhraseSpan()) {
                // We might be here during nbest list extraction, because recombination can
                // create a longer gap than seen during beam search:
                return new FeatureValue<String>(GAP_FEATURE_NAME, -100);
              } else {
                return new FeatureValue<String>(GAP_FEATURE_NAME, -1.0*distance - FUTURE_COST);
              }
            }
          }
        }
      }
    }

    return null;
  }

	public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
    return null;
  }

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
	}

	public void reset() { }
}
