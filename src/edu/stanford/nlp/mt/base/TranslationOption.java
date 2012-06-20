package edu.stanford.nlp.mt.base;

import java.util.*;

/**
 *
 * @author danielcer
 *
 * @param <T>
 */
public class TranslationOption<T> implements Comparable<TranslationOption>{

  public final int id;
  public final float[] scores;
  public final String[] phraseScoreNames;
  public final RawSequence<T> translation;
  public final RawSequence<T> foreign;
  public final PhraseAlignment alignment;
  public final boolean forceAdd;
  private int hashCode = -1;

  public TranslationOption(float[] scores, String[] phraseScoreNames,
      RawSequence<T> translation, RawSequence<T> foreign,
      PhraseAlignment alignment) {
    this(0, scores, phraseScoreNames, translation, foreign, alignment);
  }

  public TranslationOption(float[] scores, String[] phraseScoreNames,
      RawSequence<T> translation, RawSequence<T> foreign,
      PhraseAlignment alignment, boolean forceAdd) {
    this(0, scores, phraseScoreNames, translation, foreign, alignment, forceAdd);
  }

  /**
	 *
	 */
  public TranslationOption(int id, float[] scores, String[] phraseScoreNames,
      RawSequence<T> translation, RawSequence<T> foreign,
      PhraseAlignment alignment) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.translation = translation;
    this.foreign = foreign;
    this.phraseScoreNames = phraseScoreNames;
    this.forceAdd = false;
  }

  public TranslationOption(int id, float[] scores, String[] phraseScoreNames,
      RawSequence<T> translation, RawSequence<T> foreign,
      PhraseAlignment alignment, boolean forceAdd) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.translation = translation;
    this.foreign = foreign;
    this.phraseScoreNames = phraseScoreNames;
    this.forceAdd = forceAdd;
  }

  @Override
  public String toString() {
    StringBuilder sbuf = new StringBuilder();
    sbuf.append(String.format("TranslationOption: \"%s\" scores: %s\n",
        translation, Arrays.toString(scores)));
    return sbuf.toString();
  }

  @Override
  public int hashCode() {
    if (hashCode == -1)
      hashCode = super.hashCode();
    return hashCode;
  }

  public boolean hasTargetGap() {
    return false;
  }

  @Override
  public int compareTo(TranslationOption o) {
    for (int i = 0; i < Math.min(o.scores.length, scores.length); i++) {
      if (o.scores[i] != scores[i]) {
        return (int)Math.signum(scores[i] - o.scores[i]);
      }
    }
    return scores.length - o.scores.length;
  }

}
