package edu.stanford.nlp.mt.base;

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
  public final Collection<FeatureValue<FV>> features;

	/**
	 * 
	 */
	public FeaturizedTranslation(Sequence<TK> translation, Collection<FeatureValue<FV>> features) {
		this.translation = translation;
		this.features = (features == null ? null : ( (features instanceof SparseFeatureValueCollection) ?
       new SparseFeatureValueCollection<FV>((SparseFeatureValueCollection<FV>)features) : new ArrayList<FeatureValue<FV>>(features)) );
	}

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(translation).append(" |||");
    for (FeatureValue fv : features)
      sb.append(" ").append(fv.name).append(": ").append(fv.value);
    return sb.toString();
  }
}
