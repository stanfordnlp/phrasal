package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * A translation rule.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <T>
 */
public class Rule<T> implements Comparable<Rule<T>>{
  
  /**
   * The id of this rule in the phrase table.
   */
  public final int id;
  
  /**
   * The phrase table rule scores.
   */
  public final float[] scores;
  
  /**
   * The features names of <code>scores</code>.
   */
  public final String[] phraseScoreNames;
  
  /**
   * The target side of the rule. Not final because for prefix decoding we sometimes want to
   * modify existing rules.
   */
  public Sequence<T> target;
  
  /**
   * The source side of the rule.
   */
  public final Sequence<T> source;
  
  /**
   * The source/target word-word alignments.
   */
  public final PhraseAlignment alignment;
  
  /**
   * The phrase table from which this rule was queried.
   */
  public final String phraseTableName;
  
  /**
   * Dynamic translation model lexicalized reordering information.
   */
  public float[] reoderingScores;
  public ReorderingTypes forwardOrientation;
  public ReorderingTypes backwardOrientation;

  /**
   * Constructor for synthetic rules, which typically are generated at runtime
   * and contained faked-up scores and/or alignments.
   * 
   * @param scores
   * @param phraseScoreNames
   * @param target
   * @param source
   * @param alignment
   */
  public Rule(float[] scores, String[] phraseScoreNames,
      Sequence<T> target, Sequence<T> source,
      PhraseAlignment alignment, String phraseTableName) {
    this(0, scores, phraseScoreNames, target, source, alignment, phraseTableName);
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
      Sequence<T> target, Sequence<T> source,
      PhraseAlignment alignment, String phraseTableName) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.target = target;
    this.source = source;
    this.phraseScoreNames = phraseScoreNames;
    this.phraseTableName = phraseTableName;
  }
  
  @Override
  public String toString() {
    return String.format("%s => %s ||| %s", source, target, Arrays.toString(scores));
  }

  @Override
  public int hashCode() {
    return source.hashCode() ^ target.hashCode();
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof Rule)) {
      return false;
    } else {
      Rule<T> other = (Rule<T>) o;
      return source.equals(other.source) && target.equals(other.target);
    }
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
