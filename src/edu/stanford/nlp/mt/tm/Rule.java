package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.MurmurHash;
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

  private static final int SYNTHETIC_RULE_ID = -1;
  
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
   * The target side of the rule.
   */
  public final Sequence<T> target;
  
  /**
   * The source side of the rule.
   */
  public final Sequence<T> source;
  
  /**
   * The source/target word-word alignments.
   */
  public final PhraseAlignment alignment;
    
  private int hashCode = -1;

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
      PhraseAlignment alignment) {
    this(SYNTHETIC_RULE_ID, scores, phraseScoreNames, target, source, alignment);
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
      PhraseAlignment alignment) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.target = target;
    this.source = source;
    this.phraseScoreNames = phraseScoreNames;
  }

  /**
   * True if this rule is synthetic, namely it was not extracted by PhraseExtract and thus
   * probably has faked-up scores or alignments.
   * 
   * @return
   */
  public boolean isSynthetic() { return id == SYNTHETIC_RULE_ID; }
  
  @Override
  public String toString() {
    return String.format("%s => %s ||| %s", source, target, Arrays.toString(scores));
  }

  @Override
  public int hashCode() {
    if (hashCode == -1) {
      int[] codes = new int[2];
      codes[0] = source.hashCode();
      codes[1] = source.hashCode();
      hashCode = MurmurHash.hash32(codes, codes.length, 1);
    }
    return hashCode;
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
