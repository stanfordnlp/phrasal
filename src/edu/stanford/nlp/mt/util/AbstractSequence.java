package edu.stanford.nlp.mt.util;

import java.util.Iterator;

/**
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
abstract public class AbstractSequence<T> implements Sequence<T> {

  private static final long serialVersionUID = -6825653165092103480L;

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      int position = 0;

      @Override
      public boolean hasNext() {
        return position < AbstractSequence.this.size();
      }

      @Override
      public T next() {
        return AbstractSequence.this.get(position++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public String toString(String delimiter) {
    StringBuilder sb = new StringBuilder();
    for (T token : this) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(token);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(" ");
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Sequence<T> o) {
    int sz = size();
    int osz = o.size();
    int max = Math.min(sz, osz);
    for (int i = 0; i < max; i++) {
      int cmp = ((Comparable<T>) get(i)).compareTo(o.get(i));
      if (cmp != 0) return cmp;
    }
    return sz - osz;
  }

  @Override
  public int hashCode() {
    // Ripped off from MurmurHash3
    final int c1 = 0xcc9e2d51;
    final int c2 = 0x1b873593;

    int sz = size();
    int h1 = sz*4;

    for (int i=0; i<sz; i++) {
      int k1 = get(i).hashCode();
      k1 *= c1;
      k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
      k1 *= c2;

      h1 ^= k1;
      h1 = (h1 << 13) | (h1 >>> 19);  // ROTL32(h1,13);
      h1 = h1*5+0xe6546b64;
    }
    return h1;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof Sequence)) {
      return false;
    } else {
      Sequence<T> other = (Sequence<T>) o;
      final int sz = size();
      if (sz != other.size()) {
        return false;
      }
      for (int i = 0; i < sz; ++i) {
        if ( ! this.get(i).equals(other.get(i))) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean contains(Sequence<T> subsequence) {
    int subSequenceSize = subsequence.size();
    if (subSequenceSize == 0)
      return true;
    int size = size();
    int iMax = size - subSequenceSize;
    for (int i = 0; i <= iMax; i++) {
      boolean match = true;
      for (int j = 0; j < subSequenceSize; j++) {
        if (!get(i + j).equals(subsequence.get(j))) {
          match = false;
          break;
        }
      }
      if (match)
        return true;
    }
    return false;
  }
  
  @Override
  public boolean contains(T element) {
    for (int i = 0; i < size(); i++) {
      if(get(i).equals(element)) return true;
    }
    return false;
  }

  @Override
  public boolean startsWith(Sequence<T> prefix) {
    final int prefixSize = prefix.size();
    if (prefixSize > this.size()) {
      return false;
    }
    for (int i = 0; i < prefixSize; i++) {
      if ( ! this.get(i).equals(prefix.get(i))) {
        return false;
      }
    }
    return true;
  }
}
