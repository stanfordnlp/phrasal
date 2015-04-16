package edu.stanford.nlp.mt.util;

import java.io.Serializable;
import java.util.BitSet;

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
  public int[][] f2e;
  public int[] target;

  private transient BitSet targetAligned;
  
  /**
   * No-arg constructor for deserialization.
   */
  public AlignedSentence() {}
  
  /**
   * Constructor.
   * 
   * @param source
   * @param target
   * @param f2e
   */
  public AlignedSentence(int[] source, int[] target, 
      int[][] f2e) {
    this.source = source;
    this.target = target;
    this.f2e = f2e;
  }

  public int sourceLength() { return source.length; }
  
  public int targetLength() { return target.length; }
  
  /**
   * Get the source sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getSource(TranslationModelIndex index) { 
    return IStrings.toIStringSequence(source, index); 
  }

  /**
   * Get the target sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getTarget(TranslationModelIndex index) { 
    return IStrings.toIStringSequence(target, index); 
  }
  
  /**
   * Get a BitSet indicating target aligned words. Analogous information for the
   * source can be obtained by inspecting f2e directly.
   * 
   * @return
   */
  public BitSet getTargetAlignedCoverage() {
    if (targetAligned == null) {
      targetAligned = new BitSet();
      for (int i = 0; i < f2e.length; i++) {
        for (int j = 0; j < f2e[i].length; ++j) {
          targetAligned.set(f2e[i][j]);
        }
      }
    }
    return targetAligned;
  }
}