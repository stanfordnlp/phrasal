package mt.decoder.efeat;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.*;

/**
 * Same as LinearDistortionFeaturizer, though penalty is a polynomial x^a, where a is determined
 * at construction.
 *
 * @author Michel Galley
 *
 * @param <TK>
 */
public class PolynomialDistortionFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

	public static final String DEBUG_PROPERTY = "DebugPollyDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  
  private String FEATURE_NAME = "PolyDistortion";
	private double DEGREE = 2.0;

  public PolynomialDistortionFeaturizer(String... args) {
    String divStr = args[0];
    DEGREE = Double.parseDouble(divStr);
    FEATURE_NAME += ":"+Double.toString(DEGREE); 
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {
    assert(f.linearDistortion >= 0);
    return new FeatureValue<String>(FEATURE_NAME, -Math.pow(f.linearDistortion,DEGREE));
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
