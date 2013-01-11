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
  
  public SGDUpdater(double initialRate) {
    this.rate = initialRate;
  }

  @Override
  public Counter<String> update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {
    Counter<String> newWeights = new ClassicCounter<String>(weights);
    Counter<String> gradientCopy = new ClassicCounter<String>(gradient);
    double nu = rate * (double) (1.0/((timeStep/100.0)+1.0));
    Counters.multiplyInPlace(gradientCopy, nu);
    Counters.subtractInPlace(newWeights, gradientCopy);    
    return newWeights;
  }

}
