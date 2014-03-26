package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.stats.Counter;

/**
 * Applies an online update rule to a weight vector given a gradient
 * evaluated at a time step.
 * 
 * @author Spence Green
 *
 * @param <FV>
 */
public interface OnlineUpdateRule<FV> {

  /**
   * Take a weight vector and a gradient and update the weight vector in place.
   * 
   * @param weights
   * @param gradient
   * @param timeStep
   * @param endOfEpoch
   * @return
   */
  public void update(Counter<FV> weights, Counter<FV> gradient, int timeStep, boolean endOfEpoch);
}
