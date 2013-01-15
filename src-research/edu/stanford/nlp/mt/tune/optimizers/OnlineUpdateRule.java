package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.Counter;

/**
 * Applies an online update rule.
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
   * @return
   */
  public void update(Counter<FV> weights, Counter<FV> gradient, int timeStep);
}
