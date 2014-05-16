package edu.stanford.nlp.mt.service.handlers;

import java.util.List;

import edu.stanford.nlp.ling.Sentence;

/**
 * The result of a rule query.
 * 
 * @author Spence Green
 *
 */
public class RuleQuery extends ScoredQuery {
  
  public final List<String> tgt;
  public final List<String> align;
  
  /**
   * Constructor.
   * 
   * @param target
   * @param alignment
   * @param score
   */
  public RuleQuery(List<String> target, List<String> alignment, double score) {
    super(score);
    this.tgt = target;
    this.align = alignment;
  }
  
  @Override
  public String toString() {
    return String.format("%s (%s) %.5f", Sentence.listToString(tgt), Sentence.listToString(align), score);
  }
}
