package edu.stanford.nlp.mt.tm;

import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;

/**
 * Interface for lexicalized reordering models.
 * 
 * @author Spence Green
 *
 */
public interface DynamicReorderingModel {

  /**
   * Forward orientation of the rule (w.r.t. the previous rule in the derivation).
   * 
   * @param rule
   * @return
   */
  public ReorderingTypes forwardOrientation(SampledRule rule);

  /**
   * Backward orientation of the rule (w.r.t. the next rule in the derivation).
   * 
   * @param rule
   * @return
   */
  public ReorderingTypes backwardOrientation(SampledRule rule);

  /**
   * Returns true if the phrase is aligned at the corner, and false otherwise.
   * 
   * @param rule
   * @param ei -- target index diagonal to corner of rule
   * @param fi -- source index diagonal to corner of rule
   * @param pos
   * @return
   */
  abstract boolean isPhraseAligned(SampledRule rule, int ei, int fi, RelativePos pos);
}
