package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;

/**
 * A rule sampled from the bitext.
 * 
 * Lexicalized reordering support. Presently we support word-based orientation
 * and the "msd-bidirectional-fe" mode.
 * 
 * @author Spence Green
 *
 */
public class SampledRule {
  public final int srcStartInclusive;
  public final int srcEndExclusive;
  public final int tgtStartInclusive;
  public final int tgtEndExclusive;
  public final int[] src;
  public final int[] tgt;
  public final SentencePair sentencePair;
  public double lex_e_f = 0.0;
  public double lex_f_e = 0.0;
  private final int hashCode;
  
  /**
   * Constructor.
   * 
   * @param srcStartInclusive
   * @param srcEndExclusive
   * @param tgtStartInclusive
   * @param tgtEndExclusive
   * @param sentencePair
   */
  public SampledRule(int srcStartInclusive, int srcEndExclusive, int tgtStartInclusive, int tgtEndExclusive, 
      SentencePair sentencePair) {
    assert srcEndExclusive - srcStartInclusive > 0;
    assert tgtEndExclusive - tgtStartInclusive > 0;
    this.srcStartInclusive = srcStartInclusive;
    this.srcEndExclusive = srcEndExclusive;
    this.tgtStartInclusive = tgtStartInclusive;
    this.tgtEndExclusive = tgtEndExclusive;
    this.sentencePair = sentencePair;
    this.src = new int[srcEndExclusive - srcStartInclusive];
    for (int i = 0; i < src.length; ++i) {
      src[i] = sentencePair.source(srcStartInclusive + i);
    }
    this.tgt = new int[tgtEndExclusive - tgtStartInclusive];
    for (int i = 0; i < tgt.length; ++i) {
      tgt[i] = sentencePair.target(tgtStartInclusive + i);
    }
    this.hashCode = MurmurHash.hash32(src, src.length, 1) ^ MurmurHash.hash32(tgt, tgt.length, 1);
  }
  
  /**
   * Source dimension of the rule.
   * 
   * @return
   */
  public int sourceLength() { return src.length; }
  
  /**
   * Target dimension of the rule.
   * 
   * @return
   */
  public int targetLength() { return tgt.length; }
  
  /**
   * Word-based lexicalized reordering classes. Forward orientation.
   * 
   * @return
   */
  public ReorderingTypes forwardOrientation() {
    final int f1 = srcStartInclusive - 1, 
        f2 = srcEndExclusive, 
        e1 = tgtStartInclusive - 1; 
//        e2 = tgtEndExclusive;
    
    boolean connectedMonotone = isPhraseAligned(e1, f1, RelativePos.NW);
    boolean connectedSwap = isPhraseAligned(e1, f2, RelativePos.NE);

    // Determine if Monotone or Swap:
    if (connectedMonotone && !connectedSwap)
      return ReorderingTypes.monotone;
    if (!connectedMonotone && connectedSwap)
      return ReorderingTypes.swap;

    return ReorderingTypes.discont1;
  }
  
  /**
   * Word-based lexicalized reordering classes. Backward orientation.
   * 
   * @return
   */
  public ReorderingTypes backwardOrientation() {
    final int f1 = srcStartInclusive - 1, 
        f2 = srcEndExclusive, 
//        e1 = tgtStartInclusive - 1, 
        e2 = tgtEndExclusive;
    
    boolean connectedMonotone = isPhraseAligned(e2, f2, RelativePos.SE);
    boolean connectedSwap = isPhraseAligned(e2, f1, RelativePos.SW);
    
    // Determine if Monotone or Swap:
    if (connectedMonotone && !connectedSwap)
      return ReorderingTypes.monotone;
    if (!connectedMonotone && connectedSwap)
      return ReorderingTypes.swap;
    
    return ReorderingTypes.discont1;
  }
  
  /**
   * Determine if position (ei,fi) is aligned (at the phrase level).
   * 
   * @param ei
   * @param fi
   * @param pos
   * @return
   */
  private boolean isPhraseAligned(int ei, int fi,
      RelativePos pos) {
    assert (fi >= -1 && ei >= -1);
    assert (fi <= sentencePair.sourceLength() && ei <= sentencePair.targetLength()) : String.format("%d %d %d %d", fi, sentencePair.sourceLength(),
        ei, sentencePair.targetLength());
    if (fi == -1 && ei == -1)
      return true;
    if (fi == -1 || ei == -1)
      return false;
    if (fi == sentencePair.sourceLength() && ei == sentencePair.targetLength())
      return true;
    if (fi == sentencePair.sourceLength() || ei == sentencePair.targetLength())
      return false;
    if (sentencePair.isSourceUnaligned(fi)) 
      return false;

    // Word-phrase reordering as in Moses:
    for (int eIndex : sentencePair.f2e(fi))
      if (eIndex == ei)
        return true;
    return false;
  }  

  @Override
  public String toString() {
    return String.format("%s => %s", Arrays.toString(src),
        Arrays.toString(tgt));
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof SampledRule)) {
      return false;
    } else {
      SampledRule other = (SampledRule) o;
      if (src.length != other.src.length ||
          tgt.length != other.tgt.length)
        return false;
      return Arrays.equals(src, other.src) && Arrays.equals(tgt, other.tgt);
    }
  }
  
  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * Return the compressed representation of the e2f alignments.
   * 
   * @return
   */
  public int[] e2fAll() {
    return sentencePair.e2f(tgtStartInclusive, tgtEndExclusive);
  }
  
  /**
   * Return the rule-internal target-source alignment grid.
   * 
   * @return
   */
  public int[][] e2f() {
    int eDim = tgtEndExclusive - tgtStartInclusive;
    int[][] e2f = new int[eDim][];
    for (int i = tgtStartInclusive; i < tgtEndExclusive; ++i) {
      int localIdx = i - tgtStartInclusive;
      int[] e2fI = sentencePair.e2f(i);
      int srcAlignDim = e2fI.length;
      e2f[localIdx] = new int[srcAlignDim];
      if (srcAlignDim > 0) {
        System.arraycopy(e2fI, 0, e2f[localIdx], 0, srcAlignDim);
        for (int j = 0; j < srcAlignDim; ++j) {
          e2f[localIdx][j] -= srcStartInclusive;
        }
      }
    }
    return e2f;
  }
  
  /**
   * Return the compressed representation of the f2e alignments.
   * 
   * @return
   */
  public int[] f2eAll() {
    return sentencePair.f2e(srcStartInclusive, srcEndExclusive);
  }
  
  /**
   * Return the rule-internal source-target alignment grid.
   * 
   * @return
   */
  public int[][] f2e() {
    int fDim = srcEndExclusive - srcStartInclusive;
    int[][] f2e = new int[fDim][];
    for (int i = srcStartInclusive; i < srcEndExclusive; ++i) {
      int localIdx = i - srcStartInclusive;
      int[] f2eI = sentencePair.f2e(i);
      int tgtAlignDim = f2eI.length;
      f2e[localIdx] = new int[tgtAlignDim];
      if (tgtAlignDim > 0) {
        System.arraycopy(f2eI, 0, f2e[localIdx], 0, f2e[localIdx].length);
        for (int j = 0; j < f2e[localIdx].length; ++j) {
          f2e[localIdx][j] -= tgtStartInclusive;
        }
      }
    }
    return f2e;
  }
  
  /**
   * Index into the f2e alignments.
   * 
   * @param i
   * @return
   */
  public int[] f2e(int i) {
    int srcIndex = srcStartInclusive + i;
    if (srcIndex < 0 || srcIndex >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
    return sentencePair.isSourceUnaligned(srcIndex) ? new int[0] : sentencePair.f2e(srcIndex);
  }

  /**
   * Index into the e2f alignments.
   * 
   * @param i
   * @return
   */
  public int[] e2f(int i) {
    int tgtIndex = tgtStartInclusive + i;
    if (tgtIndex < 0 || tgtIndex >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
    return sentencePair.isTargetUnaligned(tgtIndex) ? new int[0] : sentencePair.e2f(tgtIndex);
  }
}