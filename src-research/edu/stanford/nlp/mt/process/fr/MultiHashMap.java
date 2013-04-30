package edu.stanford.nlp.mt.process.fr;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * A linkedhashmap that allows multiple unique values per key.
 *  Sets of values may be put; this removes the existing set.
 *  Or values may put one at a time, and existing values persist.
 *  Values are returned as a linkedHashSet.
 * @author kevinreschke
 *
 * @param <K>
 * @param <V>
 */
public class MultiHashMap<K,V> extends LinkedHashMap<K,LinkedHashSet<V>>{

  private static final long serialVersionUID = 1L;

  /**
   * Add a single value to this key's value set.
   * Creates a new value set if none exists.
   */
  public void addValue(K key, V value) {
    LinkedHashSet<V> values = this.get(key);
    if(values == null) {
      values = new LinkedHashSet<V>();
      this.put(key, values);
    }
    values.add(value);
  }
  
}
