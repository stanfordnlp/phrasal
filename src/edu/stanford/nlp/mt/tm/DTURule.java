package edu.stanford.nlp.mt.tm;

import java.util.Arrays;

import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;

/**
 * A gappy rule.
 * 
 * @author Michel Galley
 * 
 * @param <T>
 */
public class DTURule<T> extends Rule<T> {

  public final Sequence<T>[] dtus;

  @SuppressWarnings("unchecked")
  public DTURule(int id, float[] scores, String[] phraseScoreNames,
      Sequence<T>[] dtus, Sequence<T> foreign, PhraseAlignment alignment, String phraseTableName) {
    super(id, scores, phraseScoreNames, Sequences.emptySequence(), foreign, alignment, phraseTableName);
    this.dtus = dtus;
  }

  @Override
  public String toString() {
    StringBuilder sbuf = new StringBuilder("DTURule: \"");
    for (int i = 0; i < dtus.length; ++i) {
      if (i > 0)
        sbuf.append(" ").append(DTUTable.GAP_STR.toString()).append(" ");
      sbuf.append(dtus[i].toString());
    }
    sbuf.append(String.format("\" scores: %s%n", Arrays.toString(scores)));
    return sbuf.toString();
  }

  @Override
  public boolean hasTargetGap() {
    return true;
  }

}
