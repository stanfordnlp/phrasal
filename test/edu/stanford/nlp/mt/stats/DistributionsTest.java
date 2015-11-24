package edu.stanford.nlp.mt.stats;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.stats.Distributions.Poisson;

/**
 * Test of random distributions.
 * 
 * @author Spence Green
 *
 */
public class DistributionsTest {

  /**
   * Target values are from the R implementation: dpois().
   */
  @Test
  public void poissonTest() {
    assertEquals("Mismatch", 0.3678794, Poisson.probOf(0, 1.0), 1e-6);
    assertEquals("Mismatch", 0.3678794, Poisson.probOf(1, 1.0), 1e-6);
    assertEquals("Mismatch", 0.1353353, Poisson.probOf(0, 2.0), 1e-6);
    assertEquals("Mismatch", 0.04978707, Poisson.probOf(0, 3.0), 1e-6);
    
    assertEquals("Mismatch", 0.04861075, Poisson.probOf(10, 15.0), 1e-2);
    assertEquals("Mismatch", 0.007566655, Poisson.probOf(3, 10.0), 1e-3);
    assertEquals("Mismatch", 0.0001720701, Poisson.probOf(3, 15.0), 1e-5);
  }
}
