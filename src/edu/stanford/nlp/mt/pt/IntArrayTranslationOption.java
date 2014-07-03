package edu.stanford.nlp.mt.pt;

import edu.stanford.nlp.mt.util.PhraseAlignment;

/**
 * Data structure for storing translation rules inside
 * a phrase table.
 * 
 * @author Spence Green
 *
 */
public class IntArrayTranslationOption implements Comparable<IntArrayTranslationOption> {
  public final int[] targetArray;
  public final float[] scores;
  public final PhraseAlignment alignment;
  public final int id;

  public IntArrayTranslationOption(int id, int[] targetArray, float[] scores,
      PhraseAlignment alignment) {
    this.id = id;
    this.targetArray = targetArray;
    this.scores = scores;
    this.alignment = alignment;
  }

  @Override
  public int compareTo(IntArrayTranslationOption o) {
    return (int) Math.signum(o.scores[0] - scores[0]);
  }
}
