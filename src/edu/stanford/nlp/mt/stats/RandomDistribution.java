package edu.stanford.nlp.mt.stats;

/**
 * A pdf or cdf.
 * 
 * @author Spence Green
 *
 */
public interface RandomDistribution {

  public double[] draw(double[] params, double[] hyperParams);
  
  public double mean(double[] params, double[] hyperParams);
  
  public double probOf(double[] instance);
}
