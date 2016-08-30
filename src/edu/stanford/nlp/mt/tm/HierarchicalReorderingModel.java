package edu.stanford.nlp.mt.tm;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;

/**
 * The hierarchical model of Galley and Manning (2008).
 * 
 *   NW      NE
 *   f1......f2
 * e1
 * ..
 * ..
 * ..
 * e2
 *   SW      SE
 *   
 * @author Spence Green
 *
 */
public class HierarchicalReorderingModel extends AbstractDynamicReorderingModel {

  private static final int[] MAX_ALIGN = new int[]{Integer.MAX_VALUE};
  private static final int[] MIN_ALIGN = new int[]{Integer.MIN_VALUE};
  
  @Override
  public boolean isPhraseAligned(SampledRule rule, int ei, int fi, RelativePos pos) {
    if (fi == -1 && ei == -1) // First rule in the derivation
      return true;
    if (fi == -1 || ei == -1) // Can't be aligned with anything.
      return false;

    // Last rule in the derivation
    if (fi == rule.sentencePair.sourceLength() && ei == rule.sentencePair.targetLength())
      return true;
    // Aligned at the right or bottom edge of the alignment grid.
    if (fi == rule.sentencePair.sourceLength() || ei == rule.sentencePair.targetLength())
      return false;

    // pos marks the corner in the grid to evaluate
    // check if there is a phrase with its edge aligned at this point
    return (pos == RelativePos.NW && hasBottomRight(rule.sentencePair, ei, fi))
        || (pos == RelativePos.NE && hasBottomLeft(rule.sentencePair, ei, fi))
        || (pos == RelativePos.SW && hasTopRight(rule.sentencePair, ei, fi)) 
        || (pos == RelativePos.SE && hasTopLeft(rule.sentencePair, ei, fi));
  }

  /**
   * True if the block has a rule aligned at the bottom right corner.
   * 
   * @param sentencePair
   * @param ei -- target index
   * @param fj -- source index
   * @return
   */
  private boolean hasBottomRight(SentencePair sentencePair, int ei, int fj) {
    int i_p,j_p;    
    for (i_p = ei; i_p >= 0; --i_p) { // look for first aligned target (horizontal axis)
      if ( ! sentencePair.isTargetUnaligned(i_p)) break;
    }
    for (j_p = fj; j_p >= 0; --j_p) { // look for first aligned source (vertical axis)
      if ( ! sentencePair.isSourceUnaligned(j_p)) break;
    }

    // Nothing aligned in the whole block?
    if (i_p < 0 && j_p < 0) return false;

    // Alignment outside of block?
    int[] a_i_p = sentencePair.e2f(i_p);
    int[] a_j_p = sentencePair.f2e(j_p);
    if (ArrayMath.max(a_j_p) > ei || ArrayMath.max(a_i_p) > fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int min_j = Math.min(j_p, ArrayMath.min(a_i_p));
    int min_i = Math.min(i_p, ArrayMath.min(a_j_p));

    while (min_i < i_p || min_j < j_p) {
      for (; j_p >= min_j; --j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = sentencePair.f2e(j_p);
        if (ArrayMath.max(a_j_p) > ei) return false;
        min_i = Math.min(min_i, ArrayMath.min(a_j_p));
      }
      for (; i_p >= min_i; --i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = sentencePair.e2f(i_p);
        if (ArrayMath.max(a_i_p) > fj) return false;
        min_j = Math.min(min_j, ArrayMath.min(a_i_p));
      }
    }

    return true;
  }

  /**
   * True if the block has a rule aligned at the top right corner.
   * 
   * @param sentencePair
   * @param ei
   * @param fj
   * @return
   */
  private boolean hasTopRight(SentencePair sentencePair, int ei, int fj) {
    int i_p,j_p;
    int tgtLen = sentencePair.targetLength();
    for (i_p = ei; i_p < tgtLen; ++i_p) { // look for first aligned target (horizontal axis)
      if ( ! sentencePair.isTargetUnaligned(i_p)) break;
    }
    for (j_p = fj; j_p >= 0; --j_p) { // look for first aligned source (vertical axis)
      if ( ! sentencePair.isSourceUnaligned(j_p)) break;
    }

    // Nothing aligned in the whole block?
    if (i_p == tgtLen && j_p < 0) return false;

    // Alignment outside of block?
    int[] a_i_p = sentencePair.e2f(i_p);
    int[] a_j_p = sentencePair.f2e(j_p);
    if (ArrayMath.min(a_j_p) < ei || ArrayMath.max(a_i_p) > fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int min_j = Math.min(j_p, ArrayMath.min(a_i_p));
    int max_i = Math.max(i_p, ArrayMath.max(a_j_p));

    while (max_i > i_p || min_j < j_p) {
      for (; j_p >= min_j; --j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = sentencePair.f2e(j_p);
        if (ArrayMath.min(a_j_p) < ei) return false;
        max_i = Math.max(max_i, ArrayMath.max(a_j_p));
      }
      for (; i_p <= max_i; ++i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = sentencePair.e2f(i_p);
        if (ArrayMath.max(a_i_p) > fj) return false;
        min_j = Math.min(min_j, ArrayMath.min(a_i_p));
      }
    }

    return true;
  }


  /**
   * True if the block has a rule aligned at the bottom left corner.
   * 
   * @param sentencePair
   * @param ei
   * @param fj
   * @return
   */
  private boolean hasBottomLeft(SentencePair sentencePair, int ei, int fj) {
    int i_p,j_p;    
    for (i_p = ei; i_p >= 0; --i_p) { // look for first aligned target (horizontal axis)
      if ( ! sentencePair.isTargetUnaligned(i_p)) break;
    }
    final int srcLen = sentencePair.sourceLength();
    for (j_p = fj; j_p < srcLen; ++j_p) { // look for first aligned source (vertical axis)
      if ( ! sentencePair.isSourceUnaligned(j_p)) break;
    }

    // Nothing aligned in the whole block?
    if (i_p < 0 && j_p == srcLen) return false;

    // Alignment outside of block?
    int[] a_i_p = sentencePair.e2f(i_p);
    int[] a_j_p = sentencePair.f2e(j_p);
    if (ArrayMath.max(a_j_p) > ei || ArrayMath.min(a_i_p) < fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int max_j = Math.max(j_p, ArrayMath.max(a_i_p));
    int min_i = Math.min(i_p, ArrayMath.min(a_j_p));

    while (min_i < i_p || max_j > j_p) {
      for (; j_p <= max_j; ++j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = sentencePair.f2e(j_p);
        if (ArrayMath.max(a_j_p) > ei) return false;
        min_i = Math.min(min_i, ArrayMath.min(a_j_p));
      }
      for (; i_p >= min_i; --i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = sentencePair.e2f(i_p);
        if (ArrayMath.min(a_i_p) < fj) return false;
        max_j = Math.max(max_j, ArrayMath.max(a_i_p));
      }
    }

    return true;
  }

  /**
   * True if the block has a rule aligned at the top left corner.
   * 
   * @param sentencePair
   * @param ei
   * @param fj
   * @return
   */
  private boolean hasTopLeft(SentencePair sentencePair, int ei, int fj) {
    int i_p,j_p;    
    int tgtLen = sentencePair.targetLength();
    for (i_p = ei; i_p < tgtLen; ++i_p) { // look for first aligned target (horizontal axis)
      if ( ! sentencePair.isTargetUnaligned(i_p)) break;
    }
    final int srcLen = sentencePair.sourceLength();
    for (j_p = fj; j_p < srcLen; ++j_p) { // look for first aligned source (vertical axis)
      if ( ! sentencePair.isSourceUnaligned(j_p)) break;
    }

    // Nothing aligned in the whole block?
    if (i_p == tgtLen && j_p == srcLen) return false;

    // Alignment outside of block?
    int[] a_i_p = sentencePair.e2f(i_p);
    int[] a_j_p = sentencePair.f2e(j_p);
    if (ArrayMath.min(a_j_p) < ei || ArrayMath.min(a_i_p) < fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int max_j = Math.max(j_p, ArrayMath.max(a_i_p));
    int max_i = Math.max(i_p, ArrayMath.max(a_j_p));

    while (max_i > i_p || max_j > j_p) {
      for (; j_p <= max_j; ++j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = sentencePair.f2e(j_p);
        if (ArrayMath.min(a_j_p) < ei) return false;
        max_i = Math.max(max_i, ArrayMath.max(a_j_p));
      }
      for (; i_p <= max_i; ++i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = sentencePair.e2f(i_p);
        if (ArrayMath.min(a_i_p) < fj) return false;
        max_j = Math.max(max_j, ArrayMath.max(a_i_p));
      }
    }

    return true;
  }
}
