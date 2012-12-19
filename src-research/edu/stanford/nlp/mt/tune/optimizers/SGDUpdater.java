package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Basic Stochastic Gradient Descent update rule.
 * 
 * @author Spence Green
 *
 */
public class SGDUpdater implements OnlineUpdateRule<String> {

  private final double rate;
  
  public SGDUpdater(double rate) {
    this.rate = rate;
  }

  @Override
  public Counter<String> update(Counter<String> weights,
      Counter<String> gradient) {
    Counter<String> newWeights = new ClassicCounter<String>(weights);
    Counter<String> gradientCopy = new ClassicCounter<String>(gradient);
    Counters.multiplyInPlace(gradientCopy, rate);
    Counters.subtractInPlace(newWeights, gradientCopy);
    return newWeights;
  }

}
