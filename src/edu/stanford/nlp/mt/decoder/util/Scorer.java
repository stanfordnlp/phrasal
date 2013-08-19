package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.stats.Counter;

/**
 * @author danielcer
 */
public interface Scorer<FV> {

  /**
   * @param features
   * @return a score under the current weights for the specified set of features.
   */
  public double getIncrementalScore(Collection<FeatureValue<FV>> features);
  
  /**
   * Update the scorer weights.
   * 
   * @param weights
   */
  public void updateWeights(Counter<FV> weights);
  
  /**
   * Save the weights to a file.
   * 
   * @param filename
   */
  public void saveWeights(String filename) throws IOException;
  
  /**
   * @param featureName
   * @return True if the scorer has any non-zero weights. False otherwise.
   */
  public boolean hasNonZeroWeight(FV featureName);
}
