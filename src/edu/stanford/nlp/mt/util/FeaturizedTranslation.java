package edu.stanford.nlp.mt.util;

import java.io.Serializable;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class FeaturizedTranslation<TK, FV> implements Serializable {
  private static final long serialVersionUID = -8828328579437113340L;
  
  public Sequence<TK> translation;
  public FeatureValueCollection<FV> features;

  /**
	 * 
	 */
  @SuppressWarnings("unchecked")
  public FeaturizedTranslation(Sequence<TK> translation,
      FeatureValueCollection<FV> features) {
    this.translation = translation;
    try {
      this.features = features == null ? null
          : (FeatureValueCollection<FV>) features.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(translation).append(" |||");
    for (FeatureValue<FV> fv : features)
      sb.append(" ").append(fv.name).append(": ").append(fv.value);
    return sb.toString();
  }
}
