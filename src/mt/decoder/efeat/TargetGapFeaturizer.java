package mt.decoder.efeat;

import java.util.List;

import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.base.IString;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.util.DTUHypothesis;
import mt.base.ConcreteTranslationOption;
import mt.base.DTUFeaturizable;

/**
 * @author Michel Galley
 */
public class TargetGapFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

	public static final String DEBUG_PROPERTY = "DebugTargetGapFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String GAP_FEATURE_NAME = "TargetGapLinearDistortion";

  @Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {

    if (!(f instanceof DTUFeaturizable))
      return null;
    DTUFeaturizable<TK,String> dtuF = (DTUFeaturizable<TK,String>) f;

    Featurizable<TK,String> curF = f.prior;

    // TODO: figure out why need to step back much more to find previous:
    // (better option that backtracking on each Featurizable?)
    for (int i=0; i<DTUHypothesis.getMaxTargetPhraseSpan()*3 && curF != null; ++i, curF = curF.prior)
      if(curF instanceof DTUFeaturizable) {
        if(((DTUFeaturizable<TK,String>)curF).abstractOption == dtuF.abstractOption) {
          if(dtuF.abstractOption != null)
            return new FeatureValue<String>(GAP_FEATURE_NAME, -1.0*(f.translationPosition-curF.translationPosition-1));
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
