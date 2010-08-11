package edu.stanford.nlp.mt.base;

import java.util.Arrays;

/**
 * Exends DynamicIntegerArrayIndex to make it possible to lookup prefixes of
 * arrays.
 * 
 * @author Michel Galley
 */
public final class DynamicIntegerArrayPrefixIndex extends DynamicIntegerArrayIndex {

  private int findPos(int[] key, int sz) {
    int hashCode = supplementalHash(arrayHashCode(key,sz));
    int idealIdx = hashCode & mask;

    for (int i = 0, idx = idealIdx; i < keys.length; i++, idx++) {
      if (idx >= keys.length) idx = 0;
      if (keys[idx] == null) return -idx-1;
      if (hashCodes[idx] != hashCode) continue;
      if(keys[idx].length != sz)
        continue;
      boolean same=true;
      for(int j=0; j<sz; ++j) {
        if(keys[idx][j] != key[j]) {
          same=false;
          break;
        }
      }
      if(same) return idx;
    }
    return -keys.length-1;
  }

  public synchronized int indexOf(int[] key, int sz, boolean add) {
    int pos = findPos(key, sz);
    if (pos >= 0) return values[pos];
    if (!add) return -1;
    return add(key, sz, -pos-1);
  }

  private int add(int[] key, int sz, int pos) {
    if ((load++)/(double)keys.length > MAX_LOAD) {
      sizeUp();
      pos = -findPos(key, sz)-1;
    }
    keys[pos] = Arrays.copyOf(key, sz); values[pos] = maxIndex++;
    reverseIndex[values[pos]] = pos;
    hashCodes[pos] = supplementalHash(arrayHashCode(key,sz));
    return maxIndex-1;
  }

  /**
   * Hashes the prefix of an array.
   */
  private static int arrayHashCode(int a[], int sz) {
    if (a == null)
        return 0;
    int result = 1;
    for (int i=0; i<sz; ++i)
        result = 31 * result + a[i];
    return result;
  }
}
