package edu.stanford.nlp.mt.service.handlers;

import java.util.List;

/**
 * A result of a query for a rule.
 * 
 * @author Spence Green
 *
 */
public class RuleQuery extends ScoredQuery {
  
  public final List<String> tgt;
  public final List<String> align;
  
  /**
   * Constructor.
   * @param target
   * @param alignment
   * @param score
   * @param source
   */
  public RuleQuery(List<String> target, List<String> alignment, double score) {
    super(score);
    this.tgt = target;
    this.align = alignment;
  }
}
