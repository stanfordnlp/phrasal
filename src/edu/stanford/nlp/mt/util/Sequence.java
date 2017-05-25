package edu.stanford.nlp.mt.util;

import java.io.Serializable;

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
public interface Sequence<T> extends Iterable<T>, Comparable<Sequence<T>>, Serializable {

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
  public boolean startsWith(Sequence<T> prefix);

  /**
   * Return the array of underlying elements.
   * 
   * @return
   */
  public T[] elements();
  
  /**
   * Concatenate two sequences and return a new sequence.
   * 
   * @param other
   * @return
   */
  public Sequence<T> concat(Sequence<T> other);
  
  /**
   * True if this sequence contains the subsequence. Otherwise, false.
   * 
   * @param subsequence
   * @return
   */
  public boolean contains(Sequence<T> subsequence);

  /**
   * True if this sequence contains the element. Otherwise, false.
   * 
   * @param element
   * @return
   */
  public boolean contains(T element);
  
  /**
   * Convert to String with the specified delimiter.
   * 
   * @param delimiter
   * @return
   */
  public String toString(String delimiter);
}
