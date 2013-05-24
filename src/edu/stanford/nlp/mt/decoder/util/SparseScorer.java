package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.OAIndex;

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

  private final Index<String> featureIndex;
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
    this.featureIndex = featureIndex == null ? new OAIndex<String>() : featureIndex;
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
    
    if (featureIndex.isLocked()) {
      throw new RuntimeException("Cannot update weight vector after the feature index has been locked!");
    } else {
      for (String feature : weights.keySet()) {
        featureIndex.indexOf(feature, true);
      }
    }
  }

  @Override
  public void saveWeights(String filename) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNonZeroWeight(String featureName) {
    return featureIndex.contains(featureName) && weights.getCount(featureName) != 0.0;
  }

  @Override
  public Index<String> getFeatureIndex() {
    return featureIndex;
  }
}
