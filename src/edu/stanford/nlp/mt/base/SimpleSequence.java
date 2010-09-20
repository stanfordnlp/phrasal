package edu.stanford.nlp.mt.base;

import java.util.*;

import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public class SimpleSequence<T> extends AbstractSequence<T> {
  private final Object[] elements;
  private final int start, end;

  /**
	 * 
	 */
  public SimpleSequence(boolean wrapDontCopy, T... elements) {
    if (wrapDontCopy) {
      this.elements = elements;
    } else {
      this.elements = Arrays.copyOf(elements, elements.length);
    }
    start = 0;
    end = elements.length;
  }

  /**
	 * 
	 */
  public SimpleSequence(int[] intElements, Index<T> index) {
    elements = new Object[intElements.length];
    for (int i = 0; i < intElements.length; i++) {
      elements[i] = index.get(intElements[i]);
    }
    start = 0;
    end = intElements.length;
  }

  /**
	 * 
	 */
  public SimpleSequence(T... elements) {
    this.elements = Arrays.copyOf(elements, elements.length);
    start = 0;
    end = elements.length;
  }

  private SimpleSequence(SimpleSequence<T> sequence, int start, int end) {
    this.elements = sequence.elements;
    int oldLen = sequence.size();
    if (start > end || end > oldLen) {
      throw new IndexOutOfBoundsException(String.format(
          "length: %d start index: %d end index: %d\n", oldLen, start, end));
    }
    this.start = sequence.start + start;
    this.end = sequence.start + end;
  }

  SimpleSequence(RawSequence<T> sequence, int start, int end) {
    this.elements = sequence.elements;
    int oldLen = elements.length;

    if (start > end || end > oldLen) {
      throw new IndexOutOfBoundsException(String.format(
          "length: %d start index: %d end index: %d\n", oldLen, start, end));
    }

    this.start = start;
    this.end = end;
  }

  @Override
  public Sequence<T> subsequence(int start, int end) {
    return new SimpleSequence<T>(this, start, end);
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

  public int size() {
    return end - start;
  }
}
