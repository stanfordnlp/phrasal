package edu.stanford.nlp.mt.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
   * Get all n-grams for a given order.
   * 
   * @param <TK>
   */
  public static <TK> List<Sequence<TK>> ngrams(Sequence<TK> sequence, int maxOrder) {
    int numNgrams = IntStream.range(0, maxOrder).map(i -> maxOrder - i).reduce((x,y) -> x*y).getAsInt();
    List<Sequence<TK>> ngrams = new ArrayList<>(numNgrams);
    for (int i = 0, sz = sequence.size(); i < sz; i++) {
      for (int j = i + 1, jMax = Math.min(sz, i + maxOrder); j <= jMax; j++) {
        Sequence<TK> ngram = sequence.subsequence(i, j);
        ngrams.add(ngram);
      }
    }
    return ngrams;
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
  @SuppressWarnings("unchecked")
  public static <T> Sequence<T> wrapStart(Sequence<T> sequence, T startToken) {
    Object[] arr = new Object[sequence.size() + 1];
    arr[0] = startToken;
    System.arraycopy(sequence.elements(), 0, arr, 1, sequence.size());
    return new SimpleSequence<T>(true, (T[]) arr);
  }

  /**
   * Append an end symbol to the end of a sequence.
   *
   * @param sequence
   * @param endToken
   * @return
   */
  @SuppressWarnings("unchecked")
  public static <T> Sequence<T> wrapEnd(Sequence<T> sequence, T endToken) {
    Object[] arr = new Object[sequence.size() + 1];
    System.arraycopy(sequence.elements(), 0, arr, 0, sequence.size());
    arr[sequence.size()] = endToken;
    return new SimpleSequence<T>(true, (T[]) arr);
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
  @SuppressWarnings("unchecked")
  public static <T> Sequence<T> wrapStartEnd(Sequence<T> sequence, T startToken, T endToken) {
    Object[] arr = new Object[sequence.size() + 2];
    arr[0] = startToken;
    System.arraycopy(sequence.elements(), 0, arr, 1, sequence.size());
    arr[sequence.size() + 1] = endToken;
    return new SimpleSequence<T>(true, (T[]) arr);
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

    @Override
    public Sequence<TK> subsequence(int start, int end) {
      throw new ArrayIndexOutOfBoundsException();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public TK[] elements() {
      return (TK[]) new Object[0];
    }

    @Override
    public Sequence<TK> concat(Sequence<TK> other) {
      return other;
    }
  }
}
