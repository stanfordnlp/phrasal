package edu.stanford.nlp.mt.tune.optimizers;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;

/**
 * Basic AdaGrad update rule from Duchi et al. (2010).
 * 
 * @author Sida Wang
 *
 */
public class AdaGradUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private Counter<String> sumGradSquare;

  public AdaGradUpdater(double initialRate, int expectedNumFeatures) {
    this.rate = initialRate;
    //sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
  }

  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep, boolean endOfEpoch) {

    // w_{t+1} := w_t - nu*g_t
    Set<String> zeroFeatures = new HashSet<String>();
    for (String feature : gradient.keySet()) {
      double gradf = gradient.getCount(feature);
      double sgsValue = sumGradSquare.incrementCount(feature, gradf*gradf);
      double wValue = weights.getCount(feature);
      double gValue = gradient.getCount(feature);
      double update = wValue - (rate * gValue/(Math.sqrt(sgsValue)+eps));
      if (update == 0.0) {
        zeroFeatures.add(feature);
      } else {
        weights.setCount(feature, update);
      }
    }
    
    // Filter zeros
    for (String feature : zeroFeatures) {
      weights.remove(feature);
    }
  }
}
