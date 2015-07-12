package edu.stanford.nlp.mt.util;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class RawSequence<TK> extends AbstractSequence<TK> {
  private static final long serialVersionUID = 3707809226147400460L;
  
  public final Object[] elements;

  public RawSequence() {
	  this.elements = new Object[0];
  }
  
  public RawSequence(TK[] elements) {
    this.elements = elements;
  }

  public RawSequence(Sequence<TK> sequence) {
    elements = new Object[sequence.size()];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = sequence.get(i);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public TK get(int i) {
    return (TK) elements[i];
  }

  @Override
  public int size() {
    return elements.length;
  }

  @Override
  public Sequence<TK> subsequence(int start, int end) {
    return new SimpleSequence<TK>(this, start, end);
  }
}
