package edu.stanford.nlp.mt.service.handlers;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.IString;

/**
 * 
 * @author Spence Green
 *
 */
public class RuleQuery {
  public final String src;
  public final String tgt;
  public final int srcPos;
  public final double score;
  public RuleQuery(ConcreteRule<IString,String> rule) {
    this.src = rule.abstractRule.source.toString();
    this.tgt = rule.abstractRule.target.toString();
    this.srcPos = rule.sourcePosition;
    this.score = rule.isolationScore;
  }
  public RuleQuery(String src, String tgt, int srcPos, double score) {
    this.src = src;
    this.tgt = tgt;
    this.srcPos = srcPos;
    this.score = score;
  }
}
