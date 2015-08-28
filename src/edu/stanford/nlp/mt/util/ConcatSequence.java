package edu.stanford.nlp.mt.util;

import java.util.Arrays;

/**
 * A sequence that allocates additional memory for fast concatenations.
 * 
 * @author Spence Green
 *
 * @param <TK>
 */
public class ConcatSequence<TK> extends AbstractSequence<TK> {
  
  private static final long serialVersionUID = 2560254255357955371L;

  private static final int INITIAL_CAPACITY = 16;
  
  private Object[] elements;
  private int end;

  /**
   * Constructor.
   * 
   * @param wrapDontCopy
   * @param elements
   */
  public ConcatSequence(TK[] elements) {
    int arrSize = Math.max(INITIAL_CAPACITY, 2 * elements.length);
    this.elements = Arrays.copyOf(elements, arrSize);
    end = elements.length - 1;
  }

  private ConcatSequence(TK[] elements, int end) {
    this.elements = elements;
    this.end = end;
  }

  @SuppressWarnings("unchecked")
  @Override
  public TK get(int i) {
    if (i > end) throw new ArrayIndexOutOfBoundsException(i);
    return (TK) elements[i];
  }

  @SuppressWarnings("unchecked")
  @Override
  public Sequence<TK> subsequence(int start, int end) {
    if (end > size()) throw new ArrayIndexOutOfBoundsException(end);
    TK[] elems = (TK[]) Arrays.copyOfRange(this.elements, start, end);
    return new ConcatSequence<TK>(elems);
  }

  @Override
  public int size() {
    return end + 1;
  }

  @SuppressWarnings("unchecked")
  @Override
  public TK[] elements() {
    return size() == elements.length ? (TK[]) elements : (TK[]) Arrays.copyOf(elements, size());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Sequence<TK> concat(Sequence<TK> other) {
    int newSize = size() + other.size();
    if (newSize > this.elements.length) {
      TK[] newElems = (TK[]) Arrays.copyOf(this.elements, newSize << 1);
      System.arraycopy(other.elements(), 0, newElems, size(), other.size());
      return new ConcatSequence<TK>(newElems, newSize - 1);
    } else {
      System.arraycopy(other.elements(), 0, this.elements, size(), other.size());
      int newEnd = end + other.size();
      return new ConcatSequence<TK>((TK[]) this.elements, newEnd);
    }
  }
  
  /**
   * Return an empty sequence.
   * 
   * @return
   */
  @SuppressWarnings("unchecked")
  public static <TK> Sequence<TK> emptySequence() {
    return new ConcatSequence<TK>((TK[]) new Object[INITIAL_CAPACITY], -1);
  }
}
