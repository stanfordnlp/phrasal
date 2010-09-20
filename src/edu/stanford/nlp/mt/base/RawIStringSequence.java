package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class RawIStringSequence extends AbstractSequence<IString> {
  public final int[] elements;

  public RawIStringSequence(int[] elements) {
    this.elements = elements;
  }

  public RawIStringSequence(IString[] elements) {
    this.elements = new int[elements.length];
    for (int i = 0; i < elements.length; i++) {
      this.elements[i] = elements[i].getId();
    }
  }

  public RawIStringSequence(Sequence<IString> sequence) {
    elements = new int[sequence.size()];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = sequence.get(i).getId();
    }
  }

  public RawIStringSequence(int[] intElements, Index<IString> index) {
    elements = new int[intElements.length];
    System.arraycopy(intElements, 0, elements, 0, intElements.length);
  }

  @Override
  public IString get(int i) {
    return new IString(elements[i]);
  }

  @Override
  public int size() {
    return elements.length;
  }

}
