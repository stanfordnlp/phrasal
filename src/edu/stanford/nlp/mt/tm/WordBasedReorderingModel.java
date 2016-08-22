package edu.stanford.nlp.mt.tm;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;

/**
 * The word-based models of Moses.
 * 
 * @author Spence Green
 *
 */
public class WordBasedReorderingModel extends AbstractDynamicReorderingModel {

  /**
   * Determine if position (ei,fi) is aligned at the word-level by checking for an alignment
   * point at the corner of the rule.
   * 
   * @param rule
   * @param ei
   * @param fi
   * @return
   */
  @Override
  public boolean isPhraseAligned(SampledRule rule, int ei, int fi, RelativePos pos) {
    assert (fi >= -1 && ei >= -1);
    assert (fi <= rule.sentencePair.sourceLength() && ei <= rule.sentencePair.targetLength()) : 
      String.format("%d %d %d %d", fi, rule.sentencePair.sourceLength(), ei, rule.sentencePair.targetLength());
    if (fi == -1 && ei == -1)
      return true;
    if (fi == -1 || ei == -1)
      return false;
    if (fi == rule.sentencePair.sourceLength() && ei == rule.sentencePair.targetLength())
      return true;
    if (fi == rule.sentencePair.sourceLength() || ei == rule.sentencePair.targetLength())
      return false;

    return rule.sentencePair.isSourceUnaligned(fi) ? false : ArrayMath.indexOf(ei, rule.sentencePair.f2e(fi)) >= 0;
  }
}
