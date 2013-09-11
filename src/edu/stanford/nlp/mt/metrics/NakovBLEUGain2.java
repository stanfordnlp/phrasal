package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * Nakov bleu with variable reference length smoothing.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class NakovBLEUGain2<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;

  private final int order;

  public NakovBLEUGain2() {
    this(DEFAULT_ORDER);
  }

  public NakovBLEUGain2(int order) {
    this.order = order;
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
    double lengthIncrement = Math.max(1.0, minLength * 0.1);

    return BLEUMetric.computeLocalSmoothScore(translation, references, null, order, true, lengthIncrement) * minLength;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
