package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import java.util.ArrayList;

/**
 * Basic AdaGrad update rule from Duchi et al. (2010).
 * 
 * @author Sida Wang
 *
 */
public class AdaGradFastFOBOSUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private final int expectedUpdateNumber = 5000;
  private double L2lambda;
  private double L1lambda;
  
  private Counter<String> sumGradSquare;
  private Counter<String> iterlastUpdate;
  private ArrayList<Double> sumL1Lambda;
  private ArrayList<Double> sumL2Lambda;
  
  public AdaGradFastFOBOSUpdater(double initialRate, int expectedNumFeatures, double L2lambda, double L1lambda) {
    this.rate = initialRate;
    this.L2lambda = L2lambda;
    this.L1lambda = L1lambda;
    
    sumL1Lambda = new ArrayList(expectedUpdateNumber);
    sumL2Lambda = new ArrayList(expectedUpdateNumber);
    
    //sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
  }

  
  //the gradient should not include any regularization terms
  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {

    // w_{t+1} := w_t - nu*g_t
    for (String feature : gradient.keySet()) {
      double gradf = gradient.getCount(feature);
      double sgsValue = sumGradSquare.incrementCount(feature, gradf*gradf);
      double wValue = weights.getCount(feature);
      double gValue = gradient.getCount(feature);
      double update = wValue - (rate * gValue/(Math.sqrt(sgsValue)+eps));
      weights.setCount(feature, update);
    }
  }
}
