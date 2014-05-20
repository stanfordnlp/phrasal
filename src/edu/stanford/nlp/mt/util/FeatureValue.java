package edu.stanford.nlp.mt.util;

/**
 * A feature key/value pair.
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public class FeatureValue<T> {
  public final double value;
  public final T name;
  
  // Some featurizers attempt to cache feature values for later lookup. This flag
  // instructs those featurizers not to cache this feature value.
  public boolean doNotCache = false;

  /**
   * Constructor.
   * 
   * @param name
   * @param value
   */
  public FeatureValue(T name, double value) {
    if (name == null) {
      throw new RuntimeException("Feature name cannot be null");
    }
    this.name = name;
    this.value = value;
  }

  @Override
  public String toString() {
    return String.format("%s:%f", name, value);
  }

  @Override
  public int hashCode() {
    return (((name == null) ? 0 : name.hashCode()) << 16)
        ^ (new Double(value).hashCode());
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (o == this)
      return true;
    if (o == null || o.getClass() != getClass())
      return false;
    FeatureValue<T> fv = (FeatureValue<T>) o;
    return (fv.value == this.value && fv.name.equals(this.name));
  }
}
