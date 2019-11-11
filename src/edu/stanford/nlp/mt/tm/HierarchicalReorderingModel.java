package edu.stanford.nlp.mt.tm;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

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

  private static final int max(int[] arr) {
    return arr.length == 0 ? Integer.MIN_VALUE : ArrayMath.max(arr);
  }

  private static final int min(int[] arr) {
    return arr.length == 0 ? Integer.MAX_VALUE : ArrayMath.min(arr);
  }

  private static final Set<Integer> EMPTY_ARRAY = new TreeSet<>();

  private static final Set<Integer> e2f(SentencePair sentencePair, int i) {
    return (i < 0 || i >= sentencePair.targetLength()) ? EMPTY_ARRAY : sentencePair.e2f(i);
  }

  private static final Set<Integer> f2e(SentencePair sentencePair, int j) {
    return (j < 0 || j >= sentencePair.sourceLength()) ? EMPTY_ARRAY : sentencePair.f2e(j);
  }

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
    Set<Integer> a_i_p = e2f(sentencePair, i_p);
    Set<Integer> a_j_p = f2e(sentencePair, j_p);

    if (Collections.max(a_j_p) > ei || Collections.max(a_i_p) > fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int min_j = Math.min(j_p, Collections.min(a_i_p));
    int min_i = Math.min(i_p, Collections.min(a_j_p));

    while (min_i <= i_p || min_j <= j_p) {
      for (; j_p >= min_j; --j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = f2e(sentencePair, j_p);
        if (Collections.max(a_j_p) > ei) return false;
        min_i = Math.min(min_i, Collections.min(a_j_p));
      }
      for (; i_p >= min_i; --i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = e2f(sentencePair, i_p);
        if (Collections.max(a_i_p) > fj) return false;
        min_j = Math.min(min_j, Collections.min(a_i_p));
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
    Set<Integer> a_i_p = e2f(sentencePair, i_p);
    Set<Integer> a_j_p = f2e(sentencePair, j_p);
    if (Collections.min(a_j_p) < ei || Collections.max(a_i_p) > fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int min_j = Math.min(j_p, Collections.min(a_i_p));
    int max_i = Math.max(i_p, Collections.max(a_j_p));

    while (max_i >= i_p || min_j <= j_p) {
      for (; j_p >= min_j; --j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = f2e(sentencePair, j_p);
        if (Collections.min(a_j_p) < ei) return false;
        max_i = Math.max(max_i, Collections.max(a_j_p));
      }
      for (; i_p <= max_i; ++i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = e2f(sentencePair, i_p);
        if (Collections.max(a_i_p) > fj) return false;
        min_j = Math.min(min_j, Collections.min(a_i_p));
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
    Set<Integer> a_i_p = e2f(sentencePair, i_p);
    Set<Integer> a_j_p = f2e(sentencePair, j_p);
    if (Collections.max(a_j_p) > ei || Collections.min(a_i_p) < fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int max_j = Math.max(j_p, Collections.max(a_i_p));
    int min_i = Math.min(i_p, Collections.min(a_j_p));

    while (min_i <= i_p || max_j >= j_p) {
      for (; j_p <= max_j; ++j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = f2e(sentencePair, j_p);
        if (Collections.max(a_j_p) > ei) return false;
        min_i = Math.min(min_i, Collections.min(a_j_p));
      }
      for (; i_p >= min_i; --i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = e2f(sentencePair, i_p);
        if (Collections.min(a_i_p) < fj) return false;
        max_j = Math.max(max_j, Collections.max(a_i_p));
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
    Set<Integer> a_i_p = e2f(sentencePair, i_p);
    Set<Integer> a_j_p = f2e(sentencePair, j_p);
    if (Collections.min(a_j_p) < ei || Collections.min(a_i_p) < fj) return false;

    // This alignment point is part of a rule that orients with the corner ei,fj
    int max_j = Math.max(j_p, Collections.max(a_i_p));
    int max_i = Math.max(i_p, Collections.max(a_j_p));

    while (max_i >= i_p || max_j >= j_p) {
      for (; j_p <= max_j; ++j_p) {
        if (sentencePair.isSourceUnaligned(j_p)) continue;
        a_j_p = f2e(sentencePair, j_p);
        if (Collections.min(a_j_p) < ei) return false;
        max_i = Math.max(max_i, Collections.max(a_j_p));
      }
      for (; i_p <= max_i; ++i_p) {
        if (sentencePair.isTargetUnaligned(i_p)) continue;
        a_i_p = e2f(sentencePair, i_p);
        if (Collections.min(a_i_p) < fj) return false;
        max_j = Math.max(max_j, Collections.max(a_i_p));
      }
    }

    return true;
  }
}
