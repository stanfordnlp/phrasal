package edu.stanford.nlp.mt.stats;

/**
 * Interface for a probability distribution.
 * 
 * @author Spence Green
 *
 */
public interface ProbabilityDistribution {

  public double[] draw(double[] params, double[] hyperParams);

  public double mean(double[] params, double[] hyperParams);

  public double probOf(double[] instance);
}
