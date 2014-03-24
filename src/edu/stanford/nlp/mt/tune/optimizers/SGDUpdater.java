package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.tune.OnlineUpdateRule;
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
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep, boolean endOfEpoch) {
    // TODO(spenceg) This is kind of hacky, but seems to work.
    final double nu = rate * (double) (1.0/((timeStep/10.0)+1.0));
    
    // w_{t+1} := w_t - nu*g_t
    Counters.addInPlace(weights, gradient, -nu);
    
    // Filter zeros
    Counters.retainNonZeros(weights);
  }

}
