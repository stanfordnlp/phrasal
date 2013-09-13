package edu.stanford.nlp.mt.service.handlers;

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
  public final String align;
  public RuleQuery(String source, String target, int srcPosition, double score, String alignment) {
    this.src = source;
    this.tgt = target;
    this.srcPos = srcPosition;
    this.score = score;
    this.align = alignment;
  }
}
