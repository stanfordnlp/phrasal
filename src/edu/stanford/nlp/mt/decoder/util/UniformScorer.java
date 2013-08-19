package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author Daniel Cer
 * 
 * @param <T>
 */
public class UniformScorer<T> implements Scorer<T> {

  public UniformScorer() {
  }

  @Override
  public double getIncrementalScore(Collection<FeatureValue<T>> features) {
    double score = 0.0;
    for (FeatureValue<T> feature : features) {
      if (feature == null)
        continue;
      score += feature.value;
    }
    return score;
  }

  @Override
  public void saveWeights(String filename) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateWeights(Counter<T> weights) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasNonZeroWeight(T featureName) {
    return true;
  }
}
