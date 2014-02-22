package edu.stanford.nlp.mt.tune.optimizers;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * Fast AdaGrad update rule from Duchi et al. (2010).
 * 
 * Lazy updates for L1 regularization.
 * 
 * Assumes a sparse gradient (i.e., no L2 regularization). REPEAT:
 * the gradient here should NOT include L2 regularization, or else there is no point.
 *
 * @author Sida Wang
 *
 */
public class AdaGradFastFOBOSUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private double L1lambda;
  
  private final Counter<String> sumGradSquare;
  private final Counter<String> lastUpdated;
  private final Counter<String> customL1;

  private final Logger logger;

  public AdaGradFastFOBOSUpdater(double initialRate, int expectedNumFeatures, double L1lambda, Counter<String> customL1) {
    this.rate = initialRate;
    this.L1lambda = L1lambda;
    sumGradSquare = new ClassicCounter<String>(expectedNumFeatures);
    lastUpdated = new ClassicCounter<String>(expectedNumFeatures);
    this.customL1 = customL1;
    
    // Setup the logger
    logger = Logger.getLogger(AdaGradFastFOBOSUpdater.class.getCanonicalName());
    SystemLogger.attach(logger, LogName.ONLINE);
  }

  @Override
  public void update(Counter<String> weights,
      Counter<String> gradient, int timeStep, boolean endOfEpoch) {

    // Special case: the weight vector is empty (initial update)
    // Special case: gradient is non-zero where the weight is 0
    Set<String> featuresToUpdate = gradient.keySet();
    if (endOfEpoch) {
      featuresToUpdate = Generics.newHashSet(weights.keySet());
      featuresToUpdate.addAll(gradient.keySet());
      logger.info(String.format("Full regularization step for %d features", featuresToUpdate.size()));
    }
        
    // w_{t+1} := w_t - nu*g_t
    Set<String> featuresToRemove = new HashSet<String>();
    for (String feature : featuresToUpdate) {
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

      // Lookup the regularization strength for this feature
      // TODO(spenceg): This is super-slow. Can we do this more quickly?
      // TODO(spenceg): DanC suggests standardizing feature names so that we just need to
      // split on some delimiter and then lookup features in a hash table
      double l1 = this.L1lambda;
      if(customL1 != null && customL1.size()>0)
        for (String prefix : customL1.keySet()) {
          if(feature.startsWith(prefix)) {
            l1 = customL1.getCount(prefix);
            break;
          }
        }

      // Update this coordinate in the weight vector
      double trunc = Math.max(0.0, (Math.abs(testupdate) - (currentrate + prevrate*idleinterval)*l1));
      double realupdate = Math.signum(testupdate) * trunc;      
      if (realupdate == 0.0) {
        featuresToRemove.add(feature);
      } else {
        weights.setCount(feature, realupdate);
      }
    }
    
    // Filter features that have been nullified
    logger.info("Nullified features: " + String.valueOf(featuresToRemove.size()));
    for (String feature : featuresToRemove) {
      weights.remove(feature);
    }
  }
}
