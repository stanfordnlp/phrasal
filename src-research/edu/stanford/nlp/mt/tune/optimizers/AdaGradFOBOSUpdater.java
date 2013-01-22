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
public class AdaGradFOBOSUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private double L1lambda;
  
  private Counter<String> sumGradSquare;

  
  public AdaGradFOBOSUpdater(double initialRate, int expectedNumFeatures, double L1lambda) {
    this.rate = initialRate;
    this.L1lambda = L1lambda;
    
    sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
  }

  // the gradient here should include L2 regularization, 
  // use the fast version if the L2 regularization is to be handled here.
  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep) {

    // w_{t+1} := w_t - nu*g_t
    for (String feature : gradient.keySet()) {
      double gradf = gradient.getCount(feature);
      double sgsValue = sumGradSquare.incrementCount(feature, gradf*gradf);
      double wValue = weights.getCount(feature);
      double gValue = gradient.getCount(feature);
      double currentrate = rate / (Math.sqrt(sgsValue)+eps);
      double testupdate = wValue - (currentrate * gValue);
      double realupdate = sign(testupdate) * pospart( Math.abs(testupdate) - currentrate*this.L1lambda );
      weights.setCount(feature, realupdate);
    }
  }
  
  private double pospart(double number)
  {
	if(number>0)
      return number;
	return 0;
  }
  
  private int sign(double number)
  {
	if(number>0)
      return 1;
	return -1;
  }
}
