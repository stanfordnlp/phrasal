package edu.stanford.nlp.mt.decoder.util;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.mt.base.DenseFeatureValueCollection;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.tune.MERT;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.math.ArrayMath;

/**
 * Score a set of features under a fixed set of weights.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class StaticScorer implements Scorer<String> {

  private OAIndex<String> featureIndex;
  private double[] weights;
  private boolean sharedFeatureIndex;

  public StaticScorer(String filename) {
    this.sharedFeatureIndex = false;
    this.featureIndex = new OAIndex<String>();
    try {
      Counter<String> wts = MERT.readWeights(filename, featureIndex);
      updateWeights(wts);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  public StaticScorer(Counter<String> featureWts) {
    this.sharedFeatureIndex = false;
    featureIndex = new OAIndex<String>();    
    updateWeights(featureWts);
  }

  public StaticScorer(Counter<String> featureWts, OAIndex<String> featureIndex) {
    this.sharedFeatureIndex = true;
    this.featureIndex = featureIndex;
    updateWeights(featureWts);
  }

  @Override
  public double getIncrementalScore(Collection<FeatureValue<String>> features) {
    return (sharedFeatureIndex && features instanceof DenseFeatureValueCollection) ?
      getIncrementalScoreInnerProduct((DenseFeatureValueCollection<String>) features) :
        getIncrementalScoreHash(features);
  }

  private double getIncrementalScoreInnerProduct(
      DenseFeatureValueCollection<String> fva) {
    return ArrayMath.innerProduct(fva.toDoubleArray(), weights);
  }

  private double getIncrementalScoreHash(
      Collection<FeatureValue<String>> features) {
    double score = 0;

    for (FeatureValue<String> feature : features) {
      int index = featureIndex.indexOf(feature.name);
      if (index >= 0 && index < weights.length) {
        score += weights[index] * feature.value;
      }
    }

    return score;
  }

  public double getIncrementalScoreNoisy(List<FeatureValue<String>> features) {
    double score = 0;

    for (FeatureValue<String> feature : features) {
      int index = featureIndex.indexOf(feature.name);
      System.out.printf("feature: %s index: %d\n", feature.name, index);
      if (index >= 0) {
        score += weights[index] * feature.value;
        System.out.printf("\tvalue: %f contrib: %f\n", feature.value,
            weights[index] * feature.value);
      }
    }

    return score;
  }

  @Override
  public boolean hasNonZeroWeight(String featureName) {
    int idx = featureIndex.indexOf(featureName);
    return idx >= 0 && weights[idx] == weights[idx] && weights[idx] != 0;
  }

  @Override
  public void updateWeights(Counter<String> featureWts) {
    for (String key : featureWts.keySet()) {
      // TODO(spenceg) - find out what is generating 'null' model weights
      // for now, we'll just have the decoding model ignore them
      if (key == null) continue;
      featureIndex.indexOf(key, true);
    }

    weights = new double[featureIndex.size()];
    for (String key : featureWts.keySet()) {
      if (key == null) continue;
      weights[featureIndex.indexOf(key)] = featureWts.getCount(key);
    }
  }

  @Override
  public void saveWeights(String filename) throws IOException {
    throw new UnsupportedOperationException();
  }
}
