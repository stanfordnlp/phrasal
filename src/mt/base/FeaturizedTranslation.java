package mt.base;

import java.util.*;


/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class FeaturizedTranslation<TK, FV> {
	public final Sequence<TK> translation;
  public final List<FeatureValue<FV>> features;

	/**
	 * 
	 * @param translation
	 * @param features
	 */
	public FeaturizedTranslation(Sequence<TK> translation, List<FeatureValue<FV>> features) {
		this.translation = translation;
		this.features = (features == null ? null : ( (features.getClass() == mt.base.FeatureValueArray.class) ?
       new FeatureValueArray<FV>(features) : new ArrayList<FeatureValue<FV>>(features)) );
	}
}
