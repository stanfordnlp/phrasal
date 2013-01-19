package edu.stanford.nlp.mt.base;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author danielcer
 * 
 * @param <T>
 */
public class FeatureValue<T> {
  public final double value;
  public final T name;

  static WeakHashMap<Object, WeakReference<Object>> nameCache = new WeakHashMap<Object, WeakReference<Object>>();

  /**
	 */
  @SuppressWarnings("unchecked")
  public FeatureValue(T name, double value, boolean cacheName) {
    this.value = value;
    if (cacheName) {
      synchronized(nameCache) {
        WeakReference<Object> nameSub = nameCache.get(name);
        if (nameSub == null) {
          this.name = name;
          nameCache.put(name, new WeakReference<Object>(name));
        } else {
          this.name = (T) nameSub.get();
        }
      }
    } else {
      this.name = name;
    }
  }

  public FeatureValue(T name, double value) {
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
