package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;

import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

/**
 * A sparse scorer for high dimensional models.
 * 
 * NOTE: This class is not threadsafe, which is okay for the current implementation
 * in which each Inferer has its own scorer.
 * 
 * @author Spence Green
 *
 */
public class SparseScorer implements Scorer<String> {

  private Counter<String> weights;
  
  public SparseScorer(Counter<String> featureWts) {
    this(featureWts, null);
  }

  /**
   * Constructor. If featureIndex is null, then a local feature index will be used.
   * 
   * @param featureWts
   * @param featureIndex
   */
  public SparseScorer(Counter<String> featureWts, Index<String> featureIndex) {
    updateWeights(featureWts);
  }
  
  @Override
  public double getIncrementalScore(Collection<FeatureValue<String>> features) {
    double score = 0.0;
    for (FeatureValue<String> feature : features) {
      score += feature.value * weights.getCount(feature.name);
    }
    return score;
  }

  @Override
  public void updateWeights(Counter<String> weights) {
    // Do not copy the weights vector.
    this.weights = weights;
  }

  @Override
  public void saveWeights(String filename) throws IOException {
    IOTools.writeWeights(filename, weights);
  }

  @Override
  public boolean hasNonZeroWeight(String featureName) {
    // Axiomatic for sparse weight vectors
    return true;
  }
}
