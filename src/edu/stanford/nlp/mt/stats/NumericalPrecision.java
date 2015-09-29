package edu.stanford.nlp.mt.stats;

/**
 * Misc utilities for evaluating numerical precisions.
 * 
 * @author Spence Green
 *
 */
public class NumericalPrecision {

  /**
   * Returns true if the difference between the two parameters is less than
   * epsilon and false otherwise.
   * 
   * @param a
   * @param b
   * @param prec
   * @return
   */
  public static boolean equals(double a, double b, double epsilon) {
    return Math.abs(a - b) < epsilon;
  }
}
