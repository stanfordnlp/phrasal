package mt.decoder.feat;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class LinearDistortionFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

	public static final String DEBUG_PROPERTY = "DebugLinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String FEATURE_NAME = "LinearDistortion";

  public static boolean ACTIVE = true;

  @Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {
    if(ACTIVE)
      return new FeatureValue<String>(FEATURE_NAME, -1.0*f.linearDistortion);
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {		
	}

	public void reset() { }
}
