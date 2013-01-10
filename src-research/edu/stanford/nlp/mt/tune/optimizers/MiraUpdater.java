package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.ClassicCounter;
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
  public Counter<String> update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {
    Counter<String> newWeights = new ClassicCounter<String>(weights);
    Counters.addInPlace(newWeights, gradient);
    return newWeights;
  }
}
