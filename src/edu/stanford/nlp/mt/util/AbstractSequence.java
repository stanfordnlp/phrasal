package edu.stanford.nlp.mt.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

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
    return new AbstractSequenceIterator();
  }

  private class AbstractSequenceIterator implements Iterator<T> {
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
  }

  @Override
  public String toString(String delimiter) {
    return Arrays.stream(elements()).map(e -> e.toString()).collect(Collectors.joining(delimiter));
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

  private int hashCode = Integer.MAX_VALUE;

  // TODO(spenceg) Change hashcode
  @Override
  public int hashCode() {
    if (hashCode == Integer.MAX_VALUE) {
      int sz = size();
      int[] codes = new int[sz];
      for (int i = 0; i < sz; ++i) {
        codes[i] = get(i).hashCode();
      }
      hashCode = MurmurHash2.hash32(codes, sz, 1);
    }
    return hashCode;
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
