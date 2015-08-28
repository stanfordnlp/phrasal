package edu.stanford.nlp.mt.util;

import java.util.Arrays;
import java.util.List;

/**
 * A sequence of elements.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public class ArraySequence<T> extends AbstractSequence<T> {

  private static final long serialVersionUID = 8446551497177484601L;

  private Object[] elements;
  private int start, end;

  /**
   * Constructor.
   * 
   * @param wrapDontCopy
   * @param elements
   */
  public ArraySequence(boolean wrapDontCopy, T[] elements) {
    this.elements = wrapDontCopy ? elements : Arrays.copyOf(elements, elements.length);
    start = 0;
    end = elements.length;
  }

  /**
   * Constructor.
   * 
   * @param toks
   */
  public ArraySequence(List<T> toks) {
    elements = toks.toArray();
    start = 0;
    end = elements.length;
  }

  /**
   * Constructor.
   * 
   * @param elements
   */
  public ArraySequence(T[] elements) {
    this(false, elements);
  }

  /**
   * Constructor.
   * 
   * @param sequence
   */
  public ArraySequence(Sequence<T> sequence) {
    elements = new Object[sequence.size()];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = sequence.get(i);
    }
    start = 0;
    end = elements.length;
  }

  /**
   * Constructor.
   * 
   * @param sequence
   * @param start
   * @param end
   */
  private ArraySequence(ArraySequence<T> sequence, int start, int end) {
    this.elements = sequence.elements;
    int oldLen = sequence.size();
    if (start > end || end > oldLen) {
      throw new IndexOutOfBoundsException(String.format(
          "length: %d start index: %d end index: %d\n", oldLen, start, end));
    }
    this.start = sequence.start + start;
    this.end = sequence.start + end;
  }

  /**
   * Constructor.
   * 
   * @param seq
   * @param start
   * @param end
   */
  public ArraySequence(Sequence<T> seq, int start, int end) {
    this.elements = seq.elements();
    this.start = start;
    this.end = end;
  }

  @Override
  public Sequence<T> subsequence(int start, int end) {
    return new ArraySequence<T>(this, start, end);
  }

  @Override
  @SuppressWarnings("unchecked")
  public T get(int i) {
    int idx = i + start;
    if (idx >= end) {
      throw new IndexOutOfBoundsException(String.format(
          "length: %d index: %d\n", size(), i));
    }
    return (T) elements[idx];
  }

  @Override
  public int size() {
    return end - start;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T[] elements() {
    return (T[]) elements;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Sequence<T> concat(Sequence<T> other) {
    T[] newArr = (T[]) Arrays.copyOf(this.elements, this.elements.length + other.size());
    System.arraycopy(other.elements(), 0, newArr, this.elements.length, other.size());
    return new ArraySequence<T>(true, newArr);
  }
}
