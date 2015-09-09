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
   * @param dontCopy
   * @param elements
   */
  public ArraySequence(boolean dontCopy, T[] elements) {
    this.elements = dontCopy ? elements : Arrays.copyOf(elements, elements.length);
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
  public ArraySequence(Sequence<T> sequence) {
    elements = Arrays.copyOf(sequence.elements(), sequence.size());
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
    return elements[idx];
  }

  @Override
  public int size() {
    return end - start;
  }

  @Override
  public T[] elements() {
    return size() == elements.length ? elements : Arrays.copyOfRange(elements, start, end);
  }

  @Override
  public Sequence<T> concat(Sequence<T> other) {
    int newSize = size() + other.size();
    T[] newArr = Arrays.copyOf(elements(), newSize);
    System.arraycopy(other.elements(), 0, newArr, size(), other.size());
    return new ArraySequence<T>(true, newArr);
  }
}
