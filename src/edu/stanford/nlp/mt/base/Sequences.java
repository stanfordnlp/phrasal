package edu.stanford.nlp.mt.base;

import java.util.List;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * Helper functions for working with Sequences.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public final class Sequences {

  private Sequences() {}

  /**
   * Convert a sequence to its underlying list of integers.
   * 
   * @param sequence
   * @return
   */
  public static <T extends HasIntegerIdentity> int[] toIntArray(
      Sequence<T> sequence) {
    int sz = sequence.size();
    int[] intArray = new int[sz];
    for (int i = 0; i < sz; i++) {
      T token = sequence.get(i);
      intArray[i] = token.getId();
    }
    return intArray;
  }

  /**
   * 
   * @param <T>
   */
  @SuppressWarnings("unchecked")
  public static <T> int[] toIntArray(Sequence<T> sequence, Index<T> index) {
    int sz = sequence.size();
    if (sz != 0) {
      if (sequence.get(0) instanceof HasIntegerIdentity) {
        return toIntArray((Sequence<HasIntegerIdentity>) sequence);
      }
    }
    int[] intArray = new int[sz];
    for (int i = 0; i < sz; i++) {
      T token = sequence.get(i);
      intArray[i] = index.indexOf(token, true);
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
    List<String> stringList = Generics.newArrayList(sequence.size());
    for (T token : sequence) {
      stringList.add(token.toString());
    }
    return stringList;
  }
}
