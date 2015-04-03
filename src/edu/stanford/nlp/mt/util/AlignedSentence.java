package edu.stanford.nlp.mt.util;

import java.io.Serializable;

/**
 * Lightweight implementation of a training example.
 * 
 * @author Spence Green
 *
 */
public class AlignedSentence implements Serializable {
  /**
   * TODO(spenceg) Replace with kryo
   */
  private static final long serialVersionUID = 1481297672562948109L;
  
  public int[] source;
  public int[][] alignments;
  public int[] target;
  
  /**
   * Constructor.
   * 
   * @param source
   * @param target
   * @param alignments
   */
  public AlignedSentence(int[] source, int[] target, 
      int[][] alignments) {
    this.source = source;
    this.target = target;
    this.alignments = alignments;
  }
  
  public Sequence<IString> getSource() { return IStrings.getIStringSequence(source); }
  
  public Sequence<IString> getTarget() { return IStrings.getIStringSequence(target); }
}