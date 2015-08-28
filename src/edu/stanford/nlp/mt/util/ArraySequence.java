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

  private T[] elements;
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
  @SuppressWarnings("unchecked")
  public ArraySequence(List<T> toks) {
    elements = (T[]) toks.toArray();
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
  @SuppressWarnings("unchecked")
  public ArraySequence(Sequence<T> sequence) {
    elements = (T[]) new Object[sequence.size()];
    int i = 0;
    for (T item : sequence) elements[i++] = item;
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
      throw new IndexOutOfBoundsException(String.format("length: %d start index: %d end index: %dn", 
          oldLen, start, end));
    }
    this.start = sequence.start + start;
    this.end = sequence.start + end;
  }

  @Override
  public Sequence<T> subsequence(int start, int end) {
    return new ArraySequence<T>(this, start, end);
  }

  @Override
  public T get(int i) {
    int idx = i + start;
    if (idx >= end) {
      throw new IndexOutOfBoundsException(String.format("length: %d index: %dn", size(), i));
    }
    return (T) elements[idx];
  }

  @Override
  public int size() {
    return end - start;
  }

  @Override
  public T[] elements() {
    return Arrays.copyOfRange(elements, start, end);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Sequence<T> concat(Sequence<T> other) {
    T[] newArr = (T[]) new Object[size() + other.size()];
    System.arraycopy(this.elements, start, newArr, 0, size());
    int i = size();
    for(T item : other) newArr[i++] = item;
    return new ArraySequence<T>(true, newArr);
  }
}
