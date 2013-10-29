package edu.stanford.nlp.mt.service.handlers;

/**
 * A service query that has an associated score.
 * 
 * @author Spence Green
 *
 */
public abstract class ScoredQuery implements Comparable<ScoredQuery> {
  
  private static final int PRECISION = 5;
  private static final double SCALE_FACTOR = Math.pow(10, PRECISION);
  
  public double score;
  
  public ScoredQuery(double score) {
    this.score = truncate(score);
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
  public void setScore(double s) { score = truncate(s); };
  
  /**
   * Quick and dirty truncation.
   * 
   * @param value
   * @param precision
   * @return
   */
  public static double truncate(double value) {
    int temp = (int) (value * SCALE_FACTOR);
    return ((double) temp) / SCALE_FACTOR;
  }
  
  @Override
  public int compareTo(ScoredQuery o) {
    return (int) Math.signum(this.score - o.score);
  }
}
