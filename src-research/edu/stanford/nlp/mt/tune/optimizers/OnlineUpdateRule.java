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
   * Take a weight vector and a gradient and return an updated weight vector.
   * 
   * @param weights
   * @param gradient
   * @return
   */
  public Counter<FV> update(Counter<FV> weights, Counter<FV> gradient);
}
