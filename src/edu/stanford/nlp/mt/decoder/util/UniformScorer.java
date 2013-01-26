package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

import static java.lang.System.*;

/**
 * 
 * @author Daniel Cer
 * 
 * @param <T>
 */
public class UniformScorer<T> implements Scorer<T> {

  private static void warn() {
    err.println("--------------------------------------------------------");
    err.println("Warning: Creating instance of UniformScorer.");
    err.println();
    err.println("This class primarily exists for diagnostic purposes");
    err.println("and to allow for generative translation models.");
    err.println();
    err.println("Otherwise, you'll probably want to use something like");
    err.println("StaticScorer.");
    err.println("--------------------------------------------------------");
  }

  private final Index<String> featureIndex;

  public UniformScorer(Index<String> featureIndex) {
    this(true, featureIndex);
  }

  public UniformScorer(boolean warn, Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    if (warn) warn();
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

  @Override
  public Index<String> getFeatureIndex() {
    return featureIndex;
  }
}
