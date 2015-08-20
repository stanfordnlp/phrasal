package edu.stanford.nlp.mt.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper functions for working with Sequences.
 *
 * @author danielcer
 * @author Spence Green
 *
 */
public final class Sequences {
  
  @SuppressWarnings("rawtypes")
  private static final Sequence EMPTY_SEQUENCE = new EmptySequence<>();
  
  private Sequences() {}

  /**
   * Convert a sequence to its underlying list of integers.
   *
   * @param sequence
   * @return
   */
  public static int[] toIntArray(Sequence<IString> sequence) {
    int sz = sequence.size();
    int[] intArray = new int[sequence.size()];
    for (int i = 0; i < sz; i++) {
      intArray[i] = sequence.get(i).getId();
    }
    return intArray;
  }

  /**
   * Convert to to an array of ints with the index argument.
   * 
   * @param sequence
   * @param index
   * @return
   */
  public static int[] toIntArray(Sequence<IString> sequence,
      Vocabulary index) {
    int sz = sequence.size();
    int[] intArray = new int[sequence.size()];
    for (int i = 0; i < sz; i++) {
      intArray[i] = index.indexOf(sequence.get(i).toString());
    }
    return intArray;
  }
  
  /**
   * Returns true if seq starts with prefix, and false otherwise.
   *
   * @param seq
   * @param prefix
   * @return
   */
  public static <T> boolean startsWith(Sequence<T> seq, Sequence<T> prefix) {
    int seqSz = seq.size();
    int prefixSz = prefix.size();
    if (prefixSz > seqSz)
      return false;
    for (int i = 0; i < prefixSz; i++) {
      if (!seq.get(i).equals(prefix.get(i)))
        return false;
    }
    return true;
  }

  /**
   * Concatenate two sequences.
   *
   * @param a
   * @param b
   * @return
   */
  @SuppressWarnings("unchecked")
  public static <T> Sequence<T> concatenate(Sequence<T> a, Sequence<T> b) {
    if (a instanceof RawSequence && b instanceof RawSequence) {
      // Fast concatenate for raw sequence, which is exposes an array of
      // elements
      return concatenateRaw((RawSequence<T>) a, (RawSequence<T>) b);
    }
    // For general case
    Object[] abArr = new Object[a.size() + b.size()];
    for (int i = 0; i < a.size(); i++) {
      abArr[i] = a.get(i);
    }
    for (int i = 0; i < b.size(); i++) {
      abArr[i+a.size()] = b.get(i);
    }
    RawSequence<T> ab = new RawSequence<T>((T[])abArr);
    return ab;
  }

  @SuppressWarnings("unchecked")
  private static <T> Sequence<T> concatenateRaw(RawSequence<T> a, RawSequence<T> b) {
    Object[] elements = new Object[a.size() + b.size()];
    if (a.size() > 0) {
      System.arraycopy(a.elements, 0, elements, 0, a.elements.length);
    }
    if (b.size() > 0) {
      System.arraycopy(b.elements, 0, elements, a.elements.length, b.elements.length);
    }
    return new RawSequence<T>((T[]) elements);
  }

  /**
   * Convert a sequence to an array of Strings.
   *
   * @param sequence
   * @return
   */
  public static <T> String[] toStringArray(Sequence<T> sequence) {
    String[] strArr = new String[sequence.size()];
    for (int i = 0; i < strArr.length; ++i) {
      strArr[i] = sequence.get(i).toString();
    }
    return strArr;
  }

  /**
   * Convert a sequence to a List of Strings.
   *
   * @param sequence
   * @return
   */
  public static <T> List<String> toStringList(Sequence<T> sequence) {
    List<String> stringList = new ArrayList<>(sequence.size());
    for (T token : sequence) {
      stringList.add(token.toString());
    }
    return stringList;
  }

  /**
   * Append a start symbol to the beginning of a sequence.
   *
   * @param sequence
   * @param startToken
   * @return
   */
  public static <T> Sequence<T> wrapStart(Sequence<T> sequence, T startToken) {
    return new InsertedStartToken<T>(sequence, startToken);
  }

  /**
   * Append an end symbol to the end of a sequence.
   *
   * @param sequence
   * @param endToken
   * @return
   */
  public static <T> Sequence<T> wrapEnd(Sequence<T> sequence, T endToken) {
    return new InsertedEndToken<T>(sequence, endToken);
  }

  /**
   * Append a start symbol to the beginning of a sequence and an end symbol to the end
   * of a sequence.
   *
   * @param sequence
   * @param startToken
   * @param endToken
   * @return
   */
  public static <T> Sequence<T> wrapStartEnd(Sequence<T> sequence, T startToken, T endToken) {
    return new InsertedStartEndToken<T>(sequence, startToken, endToken);
  }

  /**
   * Return the empty sequence.
   * 
   * @return
   */
  @SuppressWarnings("unchecked")
  public static <T> Sequence<T> emptySequence() {
    return (Sequence<T>) EMPTY_SEQUENCE;
  }
  
  /**
   * Efficient wrapper around a sequence.
   *
   * @author Spence Green
   *
   * @param <TK>
   */
  private static class InsertedStartEndToken<TK> extends AbstractSequence<TK> {
    final Sequence<TK> wrapped;
    final TK startToken;
    final TK endToken;
    final int wrappedSz;

    public InsertedStartEndToken(Sequence<TK> wrapped, TK startToken, TK endToken) {
      this.wrapped = wrapped;
      this.startToken = startToken;
      this.endToken = endToken;
      this.wrappedSz = wrapped.size();
    }

    @Override
    public TK get(int i) {
      if (i == 0) {
        return startToken;
      }
      if (i < wrappedSz + 1) {
        return wrapped.get(i - 1);
      }
      if (i == wrappedSz + 1) {
        return endToken;
      }

      throw new IndexOutOfBoundsException(String.format(
          "Index: %d Sequence Length: %s\n", i, size()));
    }

    @Override
    public int size() {
      return wrapped.size() + 2;
    }

    @Override
    public Sequence<TK> subsequence(int start, int end) {
      if (start == 0 || end == size()) {
        return super.subsequence(start, end);
      } else {
        return wrapped.subsequence(start - 1, end - 1);
      }
    }
  }

  /**
   * Efficient wrapper around a sequence.
   *
   * @author Spence Green
   *
   * @param <TK>
   */
  private static class InsertedEndToken<TK> extends AbstractSequence<TK> {
    final Sequence<TK> wrapped;
    final TK endToken;
    final int wrappedSz;

    public InsertedEndToken(Sequence<TK> wrapped, TK endToken) {
      this.wrapped = wrapped;
      this.endToken = endToken;
      this.wrappedSz = wrapped.size();
    }

    @Override
    public TK get(int i) {
      if (i < wrappedSz) {
        return wrapped.get(i);
      }
      if (i == wrappedSz) {
        return endToken;
      }

      throw new IndexOutOfBoundsException(String.format(
          "Index: %d Sequence Length: %s\n", i, size()));
    }

    @Override
    public int size() {
      return wrapped.size() + 1;
    }

    @Override
    public Sequence<TK> subsequence(int start, int end) {
      if (end == size()) {
        return super.subsequence(start, end);
      } else {
        return wrapped.subsequence(start - 1, end - 1);
      }
    }
  }

  /**
   * Efficient wrapper around a sequence.
   *
   * @author Spence Green
   *
   * @param <TK>
   */
  private static class InsertedStartToken<TK> extends AbstractSequence<TK> {
    Sequence<TK> wrapped;
    TK startToken;

    public InsertedStartToken(Sequence<TK> wrapped, TK startToken) {
      this.wrapped = wrapped;
      this.startToken = startToken;
    }

    @Override
    public TK get(int i) {
      if (i == 0)
        return startToken;
      return wrapped.get(i - 1);
    }

    @Override
    public int size() {
      return wrapped.size() + 1;
    }

    @Override
    public Sequence<TK> subsequence(int start, int end) {
      if (start == 0) {
        return super.subsequence(start, end);
      } else {
        return wrapped.subsequence(start - 1, end - 1);
      }
    }
  }
  
  /**
   * A sequence of length 0.
   * 
   * @author danielcer
   * 
   * @param <TK>
   */
  private static class EmptySequence<TK> extends AbstractSequence<TK> {

    private static final long serialVersionUID = -7782096461898739067L;

    @Override
    public TK get(int i) {
      throw new IndexOutOfBoundsException(String.format(
          "Index: %d Sequence size: 0", i));
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public String toString() {
      return "";
    }
  }
}
