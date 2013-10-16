package edu.stanford.nlp.mt.service.handlers;

import java.math.BigDecimal;

/**
 * A service query that has an associated score.
 * 
 * @author Spence Green
 *
 */
public abstract class ScoredQuery implements Comparable<ScoredQuery> {
  
  private static final int PRECISION = 5;
  
  public double score;
  
  public ScoredQuery(double score) {
    this.score = round(score, PRECISION);
  }
  
  /**
   * Get the score.
   * 
   * @return
   */
  public double score() { return score; };
  
  /**
   * Set the score.
   * 
   * @param s
   */
  public void setScore(double s) { score = round(s, PRECISION); };
  
  /**
   * Rounds a score to a specified precision.
   * 
   * TODO(spenceg): Safe, but not sure how fast this is. Could just cast
   * to float for a quick-and-dirty solution.
   * 
   * @param value
   * @param precision
   * @return
   */
  public static double round(double value, int precision) {
    if (precision < 0) throw new IllegalArgumentException();
    BigDecimal bd = new BigDecimal(value);
    bd = bd.setScale(precision, BigDecimal.ROUND_HALF_UP);
    return bd.doubleValue();
  }
  
  @Override
  public int compareTo(ScoredQuery o) {
    return (int) Math.signum(this.score - o.score);
  }
}
