package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * Reference implementation of smoothed BLEU for sanity checking.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUSmoothGain<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;
  
  private final int order;
  private final boolean addNoise;
  
  public BLEUSmoothGain() {
    this(DEFAULT_ORDER);
  }
  
  public BLEUSmoothGain(int order) {
    this.order = order;
    this.addNoise = false;
  }
  
  public BLEUSmoothGain(boolean addNoise) {
    this.order = DEFAULT_ORDER;
    this.addNoise = addNoise;
  }

  @Override
  public double score(int sourceId, List<Sequence<TK>> references,
      double[] referenceWeights, Sequence<TK> translation) {
    // Take the min reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }
    
    double score = BLEUMetric.computeLocalSmoothScore(translation, references, referenceWeights, order, false);
    if (addNoise) {
      score = score + (Math.random() * (1.0-score));
    }
    
    // Scale the score by the min reference length
    return score * minLength;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
