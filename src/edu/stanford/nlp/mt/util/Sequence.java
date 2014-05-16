package edu.stanford.nlp.mt.util;

/**
 * Immutable sequence.
 *
 * Contract: Implementations should provide cheap construction of
 * subsequences.
 *
 * Notes: In the future, Sequence may be made into a subtype of
 * java.util.Collection or java.util.List. However, right now this would bring
 * with it a lot of methods that aren't really useful given how sequences are
 * used.
 *
 * @author Daniel Cer
 *
 * @param <T>
 */
public interface Sequence<T> extends Iterable<T>, Comparable<Sequence<T>> {

  /**
   *
   */
  T get(int i);

  /**
   *
   */
  int size();

  /**
   *
   */
  public Sequence<T> subsequence(int start, int end);

  /**
   *
   */
  public Sequence<T> subsequence(CoverageSet select);

  /**
   *
   */
  public boolean startsWith(Sequence<T> prefix);

  /**
   *
   */
  public boolean contains(Sequence<T> subsequence);

  public String toString(String delimiter);

  public String toString(String prefix, String delimiter);

  public String toString(String prefix, String delimiter, String suffix);
}
