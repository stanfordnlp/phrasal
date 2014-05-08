package edu.stanford.nlp.mt.util;


/**
 * Assign a unique integer to int[]
 */
public class ProbingIntegerArrayRawIndex implements IntegerArrayRawIndex {
  static final float LOAD = 0.7f;

  private long[] hashedKeys;
  private int[] values;
  private int mask;
  private int size;
  private int threshold;

  public ProbingIntegerArrayRawIndex() {
    size = 0;
    // Arbitrary: initial number of buckets. Cannot be zero.
    init(1024);
  }

  private void init(int buckets) {
    hashedKeys = new long[buckets];
    values = new int[buckets];
    mask = buckets - 1;
    threshold = (int)((float)buckets * LOAD) - 1;
    if (threshold < 0) threshold = 0;
  }

  /* Suggested API: separate functions for different semantics */
  public int find(int[] key) {
    long hashed = hash(key);
    for (int i = ideal(hashed); ; ++i) {
      if (i == values.length)
        i = 0;
      if (hashedKeys[i] == hashed)
        return values[i];
      if (hashedKeys[i] == 0)
        return -1;
    }
  }

  public int findOrInsert(int[] key) {
    long hashed = hash(key);
    int i;
    for (i = ideal(hashed); ; ++i) {
      if (i == values.length)
        i = 0;
      if (hashedKeys[i] == hashed)
        return values[i];
      if (hashedKeys[i] == 0)
        break;
    }
    hashedKeys[i] = hashed;
    values[i] = size;
    if (++size >= threshold) {
      grow();
    }
    return size - 1;
  }

  public int size() { return size; }


  /* IntegerArrayRawIndex API */
  public int getIndex(int[] key) {
    return find(key);
  }
  public int insertIntoIndex(int[] key) {
    return findOrInsert(key);
  }

  private static long hash(int[] key) {
    return MurmurHash.hash64(key, key.length, 1);
  }
  private int ideal(long hashed) {
    return ((int)hashed) & mask;
  }

  // Kinda ugly that it doubles peak memory.  In C++ I use realloc with an
  // in-place growth algorithm.
  private void grow() {
    long[] oldHashedKeys = hashedKeys;
    int[] oldValues = values;
    init(values.length << 1);
    for (int old = 0; old < oldHashedKeys.length; ++old) {
      if (oldHashedKeys[old] == 0) continue;
      for (int newidx = ideal(oldHashedKeys[old]); ; ++newidx) {
        if (newidx == hashedKeys.length)
          newidx = 0;
        if (hashedKeys[newidx] == 0) {
          hashedKeys[newidx] = oldHashedKeys[old];
          values[newidx] = oldValues[old];
          break;
        }
      }
    }
  }
}
