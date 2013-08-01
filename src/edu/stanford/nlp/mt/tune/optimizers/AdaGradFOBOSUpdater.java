package edu.stanford.nlp.mt.tune.optimizers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.Generics;



/**
 * Basic AdaGrad update rule from Duchi et al. (2010).
 * 
 * Assumes a dense gradient that has had L2 regularization applied.
 * 
 * @author Sida Wang
 *         Mengqiu Wang
 *
 */
public class AdaGradFOBOSUpdater implements OnlineUpdateRule<String> {

  private final double rate;

  // for flexible divisions. Think of 1/eps as the maximum
  // magnification factor over the base learning rate
  private final double eps = 1e-3;
  private double lambda;
  
  
  public enum Norm { LASSO, aeLASSO; }

  private Counter<String> sumGradSquare;
  private Norm norm;
  private Counter<String> customL1;

  public AdaGradFOBOSUpdater(double initialRate, int expectedNumFeatures, double lambda, Norm norm, Counter<String> customL1) {
    this.rate = initialRate;
    this.lambda = lambda;
    this.norm = norm;
    this.customL1 = customL1;
    
    sumGradSquare = new OpenAddressCounter<String>(expectedNumFeatures, 1.0f);
  }

  public AdaGradFOBOSUpdater(double initialRate, int expectedNumFeatures, double lambda) {
      this(initialRate, expectedNumFeatures, lambda, Norm.LASSO, null);
  }

  // public void setFeatureGroups(List<Set<String>> groups) {
  //   this.featureGroups = groups;
  // }

  // the gradient here should include L2 regularization, 
  // use the fast version if the L2 regularization is to be handled here.
  @Override
  public void update(Counter<String> weights,
		    Counter<String> gradient, int timeStep, boolean endOfEpoch) {
    if (norm == Norm.LASSO)
      updateL1(weights, gradient, timeStep);
    else if (norm == Norm.aeLASSO) {
      updateElitistLasso(weights, gradient, timeStep);
    } else 
      throw new UnsupportedOperationException("norm type " + norm + " cannot be recognized in AdaGradFOBOSUpdater");
  }

  public void updateL1(Counter<String> weights,
		     Counter<String> gradient, int timeStep) {
    // w_{t+1} := w_t - nu*g_t
    for (String feature : gradient.keySet()) {
      double gValue = gradient.getCount(feature);
      double sgsValue = sumGradSquare.incrementCount(feature, gValue*gValue);
      double wValue = weights.getCount(feature);
      double currentrate = rate / (Math.sqrt(sgsValue)+eps);
      double testupdate = wValue - (currentrate * gValue);
      double realupdate = Math.signum(testupdate) * pospart( Math.abs(testupdate) - currentrate*this.lambda );
      if (realupdate == 0.0) {
        // Filter zeros
        weights.remove(feature);
      } else {
        weights.setCount(feature, realupdate);
      }
    }
  }

  class DefaultHashMap extends HashMap<String,Set<String>> {
    @Override
    public Set<String> get(Object k) {
      Set<String> v = super.get(k);
      if ((v == null) && !this.containsKey(k)) {
        Set<String> aSet = Generics.newHashSet(1);
        this.put((String)k, aSet);
        return aSet;
      } else 
        return v;
    }
  }

  public void updateElitistLasso(Counter<String> weights,
		     Counter<String> gradient, int timeStep) {
    String PTFeat = "DiscPT.s+t:";
    String OTHERS = "OTHERS";
    int PTLen = PTFeat.length();
    
    for (String feature: gradient.keySet())
    {
        double tempgrad = gradient.getCount(feature);
        sumGradSquare.incrementCount(feature, tempgrad * tempgrad);
    }

    // Build featureGroups
    Map<String, Set<String>> featureGroups = new DefaultHashMap();
    for (String feature: sumGradSquare.keySet())
    {
    	if(feature.startsWith(PTFeat))
    	{
    	    String strip=feature.substring(PTLen);
    	    String[] sourceTarget = strip.split(">");
    	    String source = sourceTarget[0];
    	    //assert(sourceTarget.length == 2);
    	    Set<String> currentGroup = featureGroups.get(source);
    	    currentGroup.add(feature);
    	}
    	else
    	{
    	    Set<String> currentGroup = featureGroups.get(OTHERS);
    	    currentGroup.add(feature);
    	}
    }

    // the key of the map should be group signature
    // value of the map is the set of features that maps to a feature group signature
    // if (featureGroups.size() == 0)
    //  throw new RuntimeException("In invoking elitist LASSO, featureGroups must be instantiated"); 

    // need to iterate over the groups of features twice
    // in first itr, calculate per-group L1-norm
    double gValue, sgsValue,  wValue, currentrate, testupdate, realupdate, tau = 0;
    for (Set<String> fGroup: featureGroups.values()) {
      double testUpdateAbsSum = 0;
      int groupSize = fGroup.size();
      Counter<String> testUpdateCache = new OpenAddressCounter<String>(groupSize, 1.0f);
      Counter<String> currentRateCache = new OpenAddressCounter<String>(groupSize, 1.0f);
      for (String feature: fGroup) {
        gValue = gradient.getCount(feature);
        sgsValue = sumGradSquare.getCount(feature);
        wValue = weights.getCount(feature);
        currentrate = rate / (Math.sqrt(sgsValue)+eps);
        testupdate = wValue - (currentrate * gValue);
        testUpdateAbsSum += Math.abs(testupdate);
        testUpdateCache.incrementCount(feature, testupdate);
        currentRateCache.incrementCount(feature, currentrate);
      }
      for (String feature: fGroup) {
        currentrate = currentRateCache.getCount(feature);
        testupdate = testUpdateCache.getCount(feature);
        double l1 = this.lambda;
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

        tau = (currentrate * l1) / (1 + currentrate * l1 * groupSize) * testUpdateAbsSum;
        realupdate = Math.signum(testupdate) * pospart(Math.abs(testupdate) - tau);
        if (realupdate == 0.0) {
          // Filter zeros
          weights.remove(feature);
          // fGroup.remove(feature);
        } else {
          weights.setCount(feature, realupdate);
        }
      }
    }
  }

  private double pospart(double number) {
    return number > 0.0 ? number : 0.0;
  }
}
