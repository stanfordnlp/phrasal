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
  public Sequence<T> subsequence(int start, int end) {
    return new AbstractSequenceWrapper(start, end);
  }

  @Override
  public String toString(String prefix, String delimiter) {
    StringBuilder sbuf = new StringBuilder(prefix);
    int sz = size();
    for (int i = 0; i < sz; i++) {
      if (i > 0)
        sbuf.append(delimiter);
      sbuf.append(get(i));
    }
    return sbuf.toString();
  }

  @Override
  public String toString(String prefix, String delimiter, String suffix) {
    StringBuilder sbuf = new StringBuilder(prefix);
    int sz = size();
    for (int i = 0; i < sz; i++) {
      if (i > 0)
        sbuf.append(delimiter);
      sbuf.append(get(i));
    }
    sbuf.append(suffix);
    return sbuf.toString();
  }

  @Override
  public String toString(String delimiter) {
    return toString("", delimiter);
  }

  @Override
  public String toString() {
    return toString(" ");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Sequence<T> subsequence(CoverageSet select) {
    Object[] newElements = new Object[select.cardinality()];
    int sz = size();
    for (int i = 0, j = 0; i < sz; i++) {
      if (select.get(i))
        newElements[j++] = get(i);
    }
    return new RawSequence<T>((T[]) newElements);
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Sequence<T> o) {
    int sz = size();
    int osz = o.size();
    int max = Math.min(sz, osz);
    for (int i = 0; i < max; i++) {
      int cmp = ((Comparable<T>) get(i)).compareTo(o.get(i));
      if (cmp == 0)
        continue;
      return cmp;
    }
    return sz - osz;
  }

  private class AbstractSequenceWrapper extends AbstractSequence<T> {
    private static final long serialVersionUID = -2573414193728462369L;
    private final int size, start;

    public AbstractSequenceWrapper(int start, int end) {
      size = end - start;
      this.start = start;
    }

    @Override
    public T get(int i) {
      if (i >= size) {
        throw new ArrayIndexOutOfBoundsException("index: " + i + " size: "
            + size);
      }

      return AbstractSequence.this.get(start + i);
    }

    @Override
    public int size() {
      return size;
    }
  }

  private int hashCode = Integer.MAX_VALUE;

  @Override
  public int hashCode() {
    if (hashCode == Integer.MAX_VALUE) {
      int sz = size();
      int[] codes = new int[sz];
      for (int i = 0; i < sz; ++i) {
        codes[i] = get(i).hashCode();
      }
      hashCode = MurmurHash.hash32(codes, sz, 1);
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
