package edu.stanford.nlp.mt.tune.optimizers;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;

/**
 * Fast AdaGrad update rule from Duchi et al. (2010).
 * Only deals with L1
 * @author Sida Wang
 *
 */
public class AdaGradFastFOBOSUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private double L1lambda;

  private Counter<String> sumGradSquare;
  private Counter<String> lastUpdated;
  //private ArrayList<Double> sumL1Lambda;

  public AdaGradFastFOBOSUpdater(double initialRate, int expectedNumFeatures, double L1lambda) {
    this.rate = initialRate;
    this.L1lambda = L1lambda;
    sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
    lastUpdated = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
    
  }

  // the gradient here should NOT include L2 regularization, or else there is no point
  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {

    Set<String> featuresToRemove = new HashSet<String>();
    // w_{t+1} := w_t - nu*g_t
    for (String feature : gradient.keySet()) {
      double gradf = gradient.getCount(feature);
      double prevrate = rate / (Math.sqrt(sumGradSquare.getCount(feature))+eps);     
      
      // Do not start decaying the weight of a feature until it has been seen
      if(sumGradSquare.getCount(feature)==0.0)
    	  prevrate = 0;
      
      double sgsValue = sumGradSquare.incrementCount(feature, gradf*gradf);
      double currentrate = rate / (Math.sqrt(sgsValue)+eps);
      double testupdate = weights.getCount(feature) - (currentrate * gradient.getCount(feature));
      double lastUpdateTimeStep = lastUpdated.getCount(feature);
      double idleinterval = timeStep - lastUpdateTimeStep-1;
      lastUpdated.setCount(feature, (double)timeStep);
      double trunc = pospart( Math.abs(testupdate) - (currentrate + prevrate*idleinterval)*this.L1lambda);
      double realupdate = Math.signum(testupdate) * trunc;
      if (realupdate == 0.0) {
        featuresToRemove.add(feature);
      } else {
        weights.setCount(feature, realupdate);
        
      }
    }
    // Filter zeros
    for (String feature : featuresToRemove) {
      weights.remove(feature);
    }
  }

  private double pospart(double number) {
    return number > 0.0 ? number : 0.0;
  }
}
