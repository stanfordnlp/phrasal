package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * A basic Mira update rule.
 * 
 * @author Spence Green
 *
 */
public class MiraUpdater implements OnlineUpdateRule<String> {

  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep, boolean endOfEpoch) {
    Counters.addInPlace(weights, gradient);
  }
}
