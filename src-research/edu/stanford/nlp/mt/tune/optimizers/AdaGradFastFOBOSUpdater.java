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
  private Counter<String> customL1;
  //private ArrayList<Double> sumL1Lambda;

  public AdaGradFastFOBOSUpdater(double initialRate, int expectedNumFeatures, double L1lambda, Counter<String> customL1) {
    this.rate = initialRate;
    this.L1lambda = L1lambda;
    sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
    lastUpdated = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
    this.customL1 = customL1;
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
      
      double l1 = this.L1lambda;
      if(customL1 != null && customL1.size()>0)
      for (String prefix : customL1.keySet())
      {
    	  if(feature.startsWith(prefix))
	  {

    	      l1 = customL1.getCount(prefix);
     	      // System.out.println("Using custom L1 for "+prefix + " valued " + l1);
	      break;
	  }
      }
      
      double trunc = pospart( Math.abs(testupdate) - (currentrate + prevrate*idleinterval)*l1);
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
