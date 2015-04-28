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

  private static final long serialVersionUID = 1481297672562948109L;

  // 256-1, since 0 is used to indicate a null alignment in the compact
  // representation.
  public static final int MAX_SENTENCE_LENGTH = 255;
  public static final int MAX_FERTILITY = 4;
  
  public int[] source;
  public int[] target;

  protected int[] f2e;
  protected int[] e2f;

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
      int[][] f2e, int[][] e2f) throws IllegalArgumentException {
    if (source.length > MAX_SENTENCE_LENGTH) throw new IllegalArgumentException();
    if (target.length > MAX_SENTENCE_LENGTH) throw new IllegalArgumentException();
    this.source = source;
    this.target = target;
    this.f2e = flatten(f2e);
    this.e2f = flatten(e2f);
  }

  public static int[] flatten(int[][] algn) {
    int[] flatArr = new int[algn.length];
    for (int i = 0; i < flatArr.length; ++i) {
      int numLinks = Math.min(MAX_FERTILITY, algn[i].length);
      int al = 0;
      for (int j = 0; j < numLinks; ++j) {
        int pos = (algn[i][j]+1) << (j*8);
        al |= pos;
      }
      flatArr[i] = al;
    }
    return flatArr;
  }
  
  public int[] f2e(int i) {
    if (i < 0 || i >= f2e.length) throw new IndexOutOfBoundsException();
    return expand(f2e[i]);
  }
  
  public int[] e2f(int i) {
    if (i < 0 || i >= e2f.length) throw new IndexOutOfBoundsException();
    return expand(e2f[i]);
  }
  
  private int[] expand(int al) {
    if (al == 0) return new int[0];
    int numLinks = ((31 - Integer.numberOfLeadingZeros(al)) / 8) + 1;
    int[] links = new int[numLinks];
    for (int i = 0; i < numLinks; ++i) {
      int pos = i * 8;
      int mask = 0xff << pos;
      int lnk = (al & mask) >> pos;
      links[i] = lnk - 1;
    }
    return links;
  }
  
  public int sourceLength() { return source.length; }
  
  public int targetLength() { return target.length; }
  
  /**
   * Get the source sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getSource(Vocabulary index) { 
    return IStrings.toIStringSequence(source, index); 
  }

  /**
   * Get the target sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getTarget(Vocabulary index) { 
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
        int[] f2eI = f2e(i);
        for (int j = 0; j < f2eI.length; ++j) {
          targetAligned.set(f2eI[j]);
        }
      }
    }
    return targetAligned;
  }
}