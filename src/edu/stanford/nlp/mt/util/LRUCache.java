package edu.stanford.nlp.mt.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU cache with a variable size.
 * 
 * NOTE: This data structure is *not* threadsafe, so it should
 * be used as a thread-local cache.
 *  
 * @author Spence Green
 *
 * @param <K>
 * @param <V>
 */
public class LRUCache<K,V> extends LinkedHashMap<K, V> {

  private static final long serialVersionUID = -2909262195630410512L;

  private static final int DEFAULT_SIZE = 1000;
  private static final float LOAD_FACTOR = 0.75f;
  
  private final int maxSize;

  /**
   * Constructor.
   */
  public LRUCache() {
    this(DEFAULT_SIZE);
  }

  /**
   * Constructor.
   * 
   * @param maxSize
   */
  public LRUCache(int maxSize) {
    super(maxSize + 1, LOAD_FACTOR, true);
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return size() > maxSize;
  }

  /**
   * Convenience class for using primitive int[] arrays
   * as keys.
   * 
   * @author Spence Green
   *
   */
  public static class ArrayKey {
    protected final int[] key;
    protected int hashCode = 0;
    public ArrayKey(int[] key) {
      this.key = key;
    }
    @Override
    public boolean equals(Object o) {
      if (this==o) {
        return true;
      }
      else if (! (o instanceof ArrayKey)) {
        return false;
      }
      ArrayKey other = (ArrayKey) o;

      if (this.key.length != other.key.length)
        return false;

      for (int i=0; i<key.length; i++)
        if (this.key[i] != other.key[i])
          return false;

      return true;
    }
    @Override
    public int hashCode() { 
      if (hashCode == 0) {
        hashCode = Arrays.hashCode(key);
      }
      return hashCode; 
    }
  }
}
