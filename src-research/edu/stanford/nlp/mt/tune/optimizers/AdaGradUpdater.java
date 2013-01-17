package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Basic Stochastic Gradient Descent update rule.
 * 
 * @author Spence Green
 *
 */
public class AdaGradUpdater implements OnlineUpdateRule<String> {

  private final double rate;
  
  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private Counter<String> sumgradsquare;
  
  public AdaGradUpdater(double initialRate) {
    this.rate = initialRate;
    sumgradsquare = new ClassicCounter<String>(100);
  }

  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {
    
    final double nu = rate;
    
    // w_{t+1} := w_t - nu*g_t
    for (String feature : gradient.keySet()) {
      double gradf = gradient.getCount(feature);
      sumgradsquare.incrementCount(feature, gradf*gradf);
      double wValue = weights.getCount(feature);
      double gValue = gradient.getCount(feature);
      double sgsValue = sumgradsquare.getCount(feature);
      double update = wValue - (nu * gValue/(Math.sqrt(sgsValue)+eps));
      weights.setCount(feature, update);
    }
  }

}
