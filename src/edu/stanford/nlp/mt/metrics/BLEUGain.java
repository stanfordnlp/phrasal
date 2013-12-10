package edu.stanford.nlp.mt.metrics;

import java.util.List;
import java.util.Random;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * Reference implementation of smoothed BLEU for sanity checking.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUGain<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;
  
  private final int order;
  private final boolean addNoise;
  private final boolean doNakov;
  private Random random;
  
  public BLEUGain() {
    this(DEFAULT_ORDER, false);
  }
  
  public BLEUGain(boolean doNakov) {
    this(DEFAULT_ORDER, doNakov);
  }
  
  public BLEUGain(int order, boolean doNakov) {
    this(order, doNakov, false);
  }
  
  public BLEUGain(int order, boolean doNakov, boolean addNoise) {
    this.order = DEFAULT_ORDER;
    this.doNakov = doNakov;
    this.addNoise = addNoise;
    if (addNoise) {
      random = new Random();
    }
  }

  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    // Take the min reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }
    
    double score = BLEUMetric.computeLocalSmoothScore(translation, references, order, doNakov);
    if (addNoise) {
      // One-sided zero-mean Gaussian noise model
      // Empirical variance from looking at data.
      score += Math.abs(random.nextGaussian() * 0.013);
      score = Math.min(1.0, score);
      
      // Uniform noise model (too aggressive)
      // score = score + (Math.random() * (1.0-score));
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
