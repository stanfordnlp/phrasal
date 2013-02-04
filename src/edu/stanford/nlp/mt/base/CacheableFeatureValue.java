package edu.stanford.nlp.mt.base;

/**
 * A FeatureValue that the decoder should cache during feature extraction. Currently
 * featurizers that implement IsolatedPhraseFeaturizer can return these objects.
 * 
 * @author Spence Green
 *
 * @param <T>
 */
public class CacheableFeatureValue<T> extends FeatureValue<T> {

  /**
   * Create a new CacheableFeatureValue
   * 
   * @param name
   * @param value
   */
  public CacheableFeatureValue(T name, double value) {
    super(name, value);
  }
  
  /**
   * Optionally cache the feature name object.
   * 
   * @param name
   * @param value
   * @param cacheFeatureName
   */
  public CacheableFeatureValue(T name, double value, boolean cacheFeatureName) {
    super(name, value, cacheFeatureName);
  }
}
