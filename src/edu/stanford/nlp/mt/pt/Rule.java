package edu.stanford.nlp.mt.pt;

import java.util.Arrays;

import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;

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
  
  /**
   * TODO(spenceg): Not sure what this does....
   */
  public final boolean forceAdd;
  
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
   */
  public Rule(int id, float[] scores, String[] phraseScoreNames,
      Sequence<T> target, Sequence<T> source,
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
      Sequence<T> target, Sequence<T> source,
      PhraseAlignment alignment, boolean forceAdd) {
    this.id = id;
    this.alignment = alignment;
    this.scores = Arrays.copyOf(scores, scores.length);
    this.target = target;
    this.source = source;
    this.phraseScoreNames = phraseScoreNames;
    this.forceAdd = forceAdd;
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
