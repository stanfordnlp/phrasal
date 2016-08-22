package edu.stanford.nlp.mt.tm;

import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;

/**
 * Reordering orientation methods, which are shared across re-ordering models.
 * 
 * @author Spence Green
 *
 */
public abstract class AbstractDynamicReorderingModel implements DynamicReorderingModel {

  /**
   * Forward orientation.
   * 
   * @param rule
   * @return
   */
  public ReorderingTypes forwardOrientation(SampledRule rule) {
    final int f1 = rule.srcStartInclusive - 1, 
        f2 = rule.srcEndExclusive, 
        e1 = rule.tgtStartInclusive - 1; 
    //          e2 = tgtEndExclusive;

    final boolean connectedMonotone = isPhraseAligned(rule, e1, f1, RelativePos.NW);
    final boolean connectedSwap = isPhraseAligned(rule, e1, f2, RelativePos.NE);

    // Determine if Monotone or Swap:
    if (connectedMonotone && !connectedSwap)
      return ReorderingTypes.monotone;
    if (!connectedMonotone && connectedSwap)
      return ReorderingTypes.swap;

    return ReorderingTypes.discont1;
  }

  /**
   * Backward orientation.
   * 
   * @param rule
   * @return
   */
  public ReorderingTypes backwardOrientation(SampledRule rule) {
    final int f1 = rule.srcStartInclusive - 1, 
        f2 = rule.srcEndExclusive, 
        //          e1 = tgtStartInclusive - 1, 
        e2 = rule.tgtEndExclusive;

    boolean connectedMonotone = isPhraseAligned(rule, e2, f2, RelativePos.SE);
    boolean connectedSwap = isPhraseAligned(rule, e2, f1, RelativePos.SW);

    // Determine if Monotone or Swap:
    if (connectedMonotone && !connectedSwap)
      return ReorderingTypes.monotone;
    if (!connectedMonotone && connectedSwap)
      return ReorderingTypes.swap;

    return ReorderingTypes.discont1;
  }
}
