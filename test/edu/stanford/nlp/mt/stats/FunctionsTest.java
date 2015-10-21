package edu.stanford.nlp.mt.stats;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for statistical functions.
 * 
 * @author Spence Green
 *
 */
public class FunctionsTest {

  /**
   * Target values are from a table of Stirling's approximation.
   */
  @Test
  public void logFactorialTest() {
    assertEquals("Error", 0.92, Math.exp(Functions.logFactorial(1)), 1e-2);
    assertEquals("Error", 1.92, Math.exp(Functions.logFactorial(2)), 1e-2);
    assertEquals("Error", 5.84, Math.exp(Functions.logFactorial(3)), 1e-2);
    assertEquals("Error", 23.51, Math.exp(Functions.logFactorial(4)), 1e-2);
    assertEquals("Error", 118.02, Math.exp(Functions.logFactorial(5)), 1e-2);
    assertEquals("Error", 710.08, Math.exp(Functions.logFactorial(6)), 1e-2);
    assertEquals("Error", 4980.40, Math.exp(Functions.logFactorial(7)), 1e-2);
    assertEquals("Error", 39902.40, Math.exp(Functions.logFactorial(8)), 1e-2);
    assertEquals("Error", 359536.87, Math.exp(Functions.logFactorial(9)), 1e-2);
  }
}
