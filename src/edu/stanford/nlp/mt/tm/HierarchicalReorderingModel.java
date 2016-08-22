package edu.stanford.nlp.mt.tm;

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
   * @param i -- target index
   * @param j -- source index
   * @return
   */
  private boolean hasBottomRight(SentencePair sentencePair, int ei, int fj) {
    // Step 1: walk up the diagonal from the corner looking for first admissible phrase pair.
    // Break when we find it.
    int e1, e2, f1 = Integer.MAX_VALUE, f2 = Integer.MIN_VALUE;
    for (e2 = ei; e2 > 0; --e2) {
      if (sentencePair.isTargetUnaligned(e2)) continue; // left edge is unaligned
      
      f1 = Integer.MAX_VALUE;
      f2 = Integer.MIN_VALUE;
      boolean admissible = true;
      for (e1 = e2; e1 >= 0; --e1) {

        // Find range of f aligning to e1...e2:
        if (! sentencePair.isTargetUnaligned(e1)) {
          int[] fss = sentencePair.e2f(e1);
          int fmin = fss[0];
          int fmax = fss[fss.length-1];
          if (fmin < f1)
            f1 = fmin;
          if (fmax > f2)
            f2 = fmax;
        }

        // Outside of block
        if (f2 > fj) {
          admissible = false;
          break;
        }

        // Check if range [e1-e2] [f1-f2] is admissible:
        for (int fi = f1; fi <= f2; ++fi) {
          if (! sentencePair.isSourceUnaligned(fi)) {
            int[] ess = sentencePair.f2e(fi);
            int emin = ess[0];
            int emax = ess[ess.length-1];
            if (emin < e1 || emax > e2) {
              admissible = false;
              break;
            }
          }
        }
        if (admissible) break;
      }
      if (admissible) break;
    }

    // No alignment template in range
    if (f1 > f2 || f2 > fj) return false;
    
    // Step 1a: If admissible phrase pair contains the corner, then return true.
    if (e2 == ei && f2 == fj) return true;
    
    // Step 2: Can the admissible alignment template be grown back to (ei, fj)?
    
    // Grow source to the right edge
    boolean alignedRight = true;
    for(int j = f2 + 1; alignedRight && j <= fj; ++j) {
      alignedRight = sentencePair.isSourceUnaligned(j);
    }
    
    // Grow target to the bottom edge
    boolean alignedBottom = true;
    for (int i = e2 + 1; alignedBottom && i <= ei; ++i) {
      alignedBottom = sentencePair.isTargetUnaligned(i);
    }
    
    return alignedRight && alignedBottom;
  }
  
  /**
   * True if the block has a rule aligned at the bottom left corner.
   * 
   * @param sentencePair
   * @param ei -- target index
   * @param fi -- source index
   * @return
   */
  private boolean hasBottomLeft(SentencePair sentencePair, int ei, int fj) {
    // Step 1: walk up the diagonal from the corner looking for first admissible phrase pair.
    // Break when we find it.
    int e1, e2, f1 = Integer.MAX_VALUE, f2 = Integer.MIN_VALUE;
    for (e2 = ei; e2 > 0; --e2) {
      if (sentencePair.isTargetUnaligned(e2)) continue; // left edge is unaligned
      
      f1 = Integer.MAX_VALUE;
      f2 = Integer.MIN_VALUE;
      boolean admissible = true;
      for (e1 = e2; e1 >= 0; --e1) {

        // Find range of f aligning to e1...e2:
        if (! sentencePair.isTargetUnaligned(e1)) {
          int[] fss = sentencePair.e2f(e1);
          int fmin = fss[0];
          int fmax = fss[fss.length-1];
          if (fmin < f1)
            f1 = fmin;
          if (fmax > f2)
            f2 = fmax;
        }

        // Outside of block
        if (f1 < fj) {
          admissible = false;
          break;
        }

        // Check if range [e1-e2] [f1-f2] is admissible:
        for (int fi = f1; fi <= f2; ++fi) {
          if (! sentencePair.isSourceUnaligned(fi)) {
            int[] ess = sentencePair.f2e(fi);
            int emin = ess[0];
            int emax = ess[ess.length-1];
            if (emin < e1 || emax > e2) {
              admissible = false;
              break;
            }
          }
        }
        if (admissible) break;
      }
      if (admissible) break;
    }

    // No alignment template in range
    if (f1 > f2 || f1 < fj) return false;
    
    // Step 1a: If admissible phrase pair contains the corner, then return true.
    if (e2 == ei && f1 == fj) return true;
    
    // Step 2: Can the admissible alignment template be grown back to (ei, fj)?
    
    // Grow source to the left edge
    boolean alignedLeft = true;
    for(int j = f1 - 1; alignedLeft && j >= fj; --j) {
      alignedLeft = sentencePair.isSourceUnaligned(j);
    }
    
    // Grow target to the bottom edge
    boolean alignedBottom = true;
    for (int i = e2 + 1; alignedBottom && i <= ei; ++i) {
      alignedBottom = sentencePair.isTargetUnaligned(i);
    }
    
    return alignedLeft && alignedBottom;
  }
  
  /**
   * True if the block has a rule aligned at the top right corner.
   * 
   * @param sentencePair
   * @param ei -- target index
   * @param fi -- source index
   * @return
   */
  private boolean hasTopRight(SentencePair sentencePair, int ei, int fj) {
    // Step 1: walk up the diagonal from the corner looking for first admissible phrase pair.
    // Break when we find it.
    int e1, e2, f1 = Integer.MAX_VALUE, f2 = Integer.MIN_VALUE;
    int maxE = sentencePair.targetLength();
    for (e1 = ei; e1 < maxE; ++e1) {
      if (sentencePair.isTargetUnaligned(e1)) continue; // left edge is unaligned
      
      f1 = Integer.MAX_VALUE;
      f2 = Integer.MIN_VALUE;
      boolean admissible = true;
      for (e2 = e1; e2 <= maxE; ++e2) {

        // Find range of f aligning to e1...e2:
        if (! sentencePair.isTargetUnaligned(e2)) {
          int[] fss = sentencePair.e2f(e2);
          int fmin = fss[0];
          int fmax = fss[fss.length-1];
          if (fmin < f1)
            f1 = fmin;
          if (fmax > f2)
            f2 = fmax;
        }

        // Outside of block
        if (f2 > fj) {
          admissible = false;
          break;
        }

        // Check if range [e1-e2] [f1-f2] is admissible:
        for (int fi = f1; fi <= f2; ++fi) {
          if (! sentencePair.isSourceUnaligned(fi)) {
            int[] ess = sentencePair.f2e(fi);
            int emin = ess[0];
            int emax = ess[ess.length-1];
            if (emin < e1 || emax > e2) {
              admissible = false;
              break;
            }
          }
        }
        if (admissible) break;
      }
      if (admissible) break;
    }

    // No alignment template in range
    if (f1 > f2 || f2 > fj) return false;
    
    // Step 1a: If admissible phrase pair contains the corner, then return true.
    if (e1 == ei && f2 == fj) return true;
    
    // Step 2: Can the admissible alignment template be grown back to (ei, fj)?
    
    // Grow source to the right edge
    boolean alignedRight = true;
    for(int j = f2 + 1; alignedRight && j <= fj; ++j) {
      alignedRight = sentencePair.isSourceUnaligned(j);
    }
    
    // Grow target to the top edge
    boolean alignedTop = true;
    for (int i = e1 - 1; alignedTop && i >= ei; --i) {
      alignedTop = sentencePair.isTargetUnaligned(i);
    }
    
    return alignedRight && alignedTop;
  }

  /**
   * True if the block has a rule aligned at the top left corner.
   * 
   * @param sentencePair
   * @param ei -- target index
   * @param fi -- source index
   * @return
   */
  private boolean hasTopLeft(SentencePair sentencePair, int ei, int fj) {
    // Step 1: walk up the diagonal from the corner looking for first admissible phrase pair.
    // Break when we find it.
    int e1, e2, f1 = Integer.MAX_VALUE, f2 = Integer.MIN_VALUE;
    int maxE = sentencePair.targetLength();
    for (e1 = ei; e1 < maxE; ++e1) {
      if (sentencePair.isTargetUnaligned(e1)) continue; // left edge is unaligned
      
      f1 = Integer.MAX_VALUE;
      f2 = Integer.MIN_VALUE;
      boolean admissible = true;
      for (e2 = e1; e2 <= maxE; ++e2) {

        // Find range of f aligning to e1...e2:
        if (! sentencePair.isTargetUnaligned(e2)) {
          int[] fss = sentencePair.e2f(e2);
          int fmin = fss[0];
          int fmax = fss[fss.length-1];
          if (fmin < f1)
            f1 = fmin;
          if (fmax > f2)
            f2 = fmax;
        }

        // Outside of block
        if (f1 < fj) {
          admissible = false;
          break;
        }

        // Check if range [e1-e2] [f1-f2] is admissible:
        for (int fi = f1; fi <= f2; ++fi) {
          if (! sentencePair.isSourceUnaligned(fi)) {
            int[] ess = sentencePair.f2e(fi);
            int emin = ess[0];
            int emax = ess[ess.length-1];
            if (emin < e1 || emax > e2) {
              admissible = false;
              break;
            }
          }
        }
        if (admissible) break;
      }
      if (admissible) break;
    }

    // No alignment template in range
    if (f1 > f2 || f1 < fj) return false;
    
    // Step 1a: If admissible phrase pair contains the corner, then return true.
    if (e1 == ei && f1 == fj) return true;
    
    // Step 2: Can the admissible alignment template be grown back to (ei, fj)?
    
    // Grow source to the left edge
    boolean alignedLeft = true;
    for(int j = f1 - 1; alignedLeft && j >= fj; --j) {
      alignedLeft = sentencePair.isSourceUnaligned(j);
    }
    
    // Grow target to the top edge
    boolean alignedTop = true;
    for (int i = e1 - 1; alignedTop && i >= ei; --i) {
      alignedTop = sentencePair.isTargetUnaligned(i);
    }
    
    return alignedLeft && alignedTop;
  }
  
//Joern's first cut at the bottom right algorithm
//int i_p,j_p;
//for (i_p = i - 1; i_p >= 0; --i_p) { // look for next aligned target 
//  if ( ! sentencePair.isTargetUnaligned(i_p)) break;
//}
//for (j_p = j - 1; j_p >= 0; --j_p) { // look for next aligned source
//  if ( ! sentencePair.isSourceUnaligned(j_p)) break;
//}
//
//if (i_p < 0 || j_p < 0) return false;
//
//// Outside of block?
//int[] a_j_p = sentencePair.f2e(j_p);
//int[] a_i_p = sentencePair.e2f(i_p);
//if (ArrayMath.max(a_j_p) > i || ArrayMath.max(a_i_p) > j) {
//  return false; // alignment point outside of block
//}
//
//// Admissibility inside block?
//int min_j = Math.min(j_p, ArrayMath.min(a_i_p));
//int min_i = Math.min(i_p, ArrayMath.min(a_j_p));
//while (min_i < i_p || min_j < j_p) {
//  for (int j_pp = j_p; j_pp >= min_j; --j) {
//    int a_j_pp = ArrayMath.max(sentencePair.f2e(j_pp));
//    if (a_j_pp > i) return false;
//    min_i = Math.min(min_i, a_j_pp);
//  }
//  for (int i_pp = i_p; i_pp >= min_i; --i) {
//    int a_i_pp = ArrayMath.max(sentencePair.e2f(i_pp));
//    if (a_i_pp > j) return false;
//    min_j = Math.min(min_j, a_i_pp);
//  }
//}
//
//return true;
}
