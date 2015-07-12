package edu.stanford.nlp.mt.util;

import java.io.Serializable;

/**
 * A feature key/value pair.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public class FeatureValue<T> implements Serializable {
  private static final long serialVersionUID = -1016755714694934570L;
  
  public final double value;
  public final T name;
  
  // Some featurizers attempt to cache feature values for later lookup. This flag
  // instructs those featurizers not to cache this feature value.
  public boolean doNotCache = false;
  
  // This feature is a baseline dense feature.
  public final boolean isDenseFeature;

  /**
   * Constructor.
   * 
   * @param name
   * @param value
   */
  public FeatureValue(T name, double value) {
    this(name, value, false);
  }
  
  /**
   * Constructor.
   * 
   * @param name
   * @param value
   * @param isDense
   */
  public FeatureValue(T name, double value, boolean isDense) {
    assert name != null : "Feature name cannot be null";
    this.name = name;
    this.value = value;
    this.isDenseFeature = isDense;
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
    if (o == this) {
      return true;
    } else if ( ! (o instanceof FeatureValue)) {
      return false;
    } else {
      FeatureValue<T> fv = (FeatureValue<T>) o;
      return (fv.value == this.value && fv.name.equals(this.name));
    }
  }
}
