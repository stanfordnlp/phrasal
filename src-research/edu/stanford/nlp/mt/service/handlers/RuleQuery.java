package edu.stanford.nlp.mt.service.handlers;

/**
 * 
 * @author Spence Green
 *
 */
public class RuleQuery implements Comparable<RuleQuery> {
  public final String src;
  public final String tgt;
  public final double score;
  public final String align;
  public RuleQuery(String source, String target, double score, String alignment) {
    this.src = source;
    this.tgt = target;
    this.score = score;
    this.align = alignment;
  }
  
  @Override
  public int compareTo(RuleQuery o) {
    return (int) Math.signum(this.score - o.score);
  }
}
