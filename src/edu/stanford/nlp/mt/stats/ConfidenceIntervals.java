package edu.stanford.nlp.mt.stats;

/**
 * Functions for computing various confidence intervals.
 * 
 * @author Spence Green
 *
 */
public final class ConfidenceIntervals {

  private ConfidenceIntervals() {}

  /**
   * 
   * @param histogram
   * @return
   */
  public static double[][] multinomialSison(int[] histogram) {
    double[][] ci = new double[histogram.length][2];
    // TODO(spenceg) Implement R code
    return ci;
  }
  
}
