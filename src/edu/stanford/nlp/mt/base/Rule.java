package edu.stanford.nlp.mt.base;

import java.util.Arrays;

/**
 * A translation rule.
 *
 * @author danielcer
 *
 * @param <T>
 */
public class Rule<T> implements Comparable<Rule<T>>{

  // Usually only on-disk phrase tables set the rule id
  // to something other than the default. Synthetic rules
  // such as those created by UnknownWordPhraseGenerator
  // should use DEFAULT_RULE_ID.
  public static final int DEFAULT_RULE_ID = -1;
  
  public final int id;
  public final float[] scores;
  public final String[] phraseScoreNames;
  public final RawSequence<T> target;
  public final RawSequence<T> source;
  public final PhraseAlignment alignment;
  public final boolean forceAdd;
  private int hashCode = -1;

  /**
   * Constructor that uses <code>DEFAULT_RULE_ID</code>.
   * 
   * @param scores
   * @param phraseScoreNames
   * @param target
   * @param source
   * @param alignment
   */
  public Rule(float[] scores, String[] phraseScoreNames,
      RawSequence<T> target, RawSequence<T> source,
      PhraseAlignment alignment) {
    this(DEFAULT_RULE_ID, scores, phraseScoreNames, target, source, alignment);
  }

  /**
   * Constructor.
   * 
   * @param id
   * @param scores
   * @param phraseScoreNames
   * @param target
   * @param source
   * @param alignment
   */
  public Rule(int id, float[] scores, String[] phraseScoreNames,
      RawSequence<T> target, RawSequence<T> source,
      PhraseAlignment alignment) {
    this(id, scores, phraseScoreNames, target, source, alignment, false);
  }

  /**
   * Constructor.
   * 
   * @param id
   * @param scores
   * @param phraseScoreNames
   * @param target
   * @param source
   * @param alignment
   * @param forceAdd
   */
  public Rule(int id, float[] scores, String[] phraseScoreNames,
      RawSequence<T> target, RawSequence<T> source,
      PhraseAlignment alignment, boolean forceAdd) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.target = target;
    this.source = source;
    this.phraseScoreNames = phraseScoreNames;
    this.forceAdd = forceAdd;
  }

  @Override
  public String toString() {
    StringBuilder sbuf = new StringBuilder();
    sbuf.append(String.format("Rule: \"%s\" scores: %s\n",
        target, Arrays.toString(scores)));
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
  public int compareTo(Rule<T> o) {
    for (int i = 0; i < Math.min(o.scores.length, scores.length); i++) {
      if (o.scores[i] != scores[i]) {
        return (int)Math.signum(scores[i] - o.scores[i]);
      }
    }
    return scores.length - o.scores.length;
  }
}
