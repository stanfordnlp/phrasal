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
  
  public BLEUSmoothGain() {
    this(DEFAULT_ORDER);
  }
  
  public BLEUSmoothGain(int order) {
    this.order = order;
  }
  
  @Override
  public double score(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
    // Take the min reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }
    
    // Scale the score by the min reference length
    return BLEUMetric.computeLocalSmoothScore(translation, references, order) * minLength;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
