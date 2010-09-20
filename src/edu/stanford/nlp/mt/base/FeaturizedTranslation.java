package edu.stanford.nlp.mt.base;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class FeaturizedTranslation<TK, FV> {
	public final Sequence<TK> translation;
  public final FeatureValueCollection<FV> features;

	/**
	 * 
	 */
  @SuppressWarnings("unchecked")
	public FeaturizedTranslation(Sequence<TK> translation, FeatureValueCollection<FV> features) {
		this.translation = translation;
    try {
      this.features = features == null ? null : (FeatureValueCollection<FV>) features.clone();
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
