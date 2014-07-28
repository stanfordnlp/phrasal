package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * BLEU+1 (Lin and Och, 2004) with optional Nakov et al. (2012) extensions.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public class BLEUGain<TK,FV> implements SentenceLevelMetric<TK, FV> {

  private static final int DEFAULT_ORDER = 4;
  
  private final int order;
  private final boolean doNakov;
  private final boolean scaleLength;
  
  /**
   * Constructor.
   */
  public BLEUGain() {
    this(false);
  }
  
  /**
   * Constructor.
   * 
   * @param doNakov
   */
  public BLEUGain(boolean doNakov) {
    this(DEFAULT_ORDER, doNakov, true);
  }
  
  /**
   * Constructor.
   * 
   * @param order
   * @param doNakov
   * @param scaleLength
   */
  public BLEUGain(int order, boolean doNakov, boolean scaleLength) {
    this.order = order;
    this.doNakov = doNakov;
    this.scaleLength = scaleLength;
  }

  @Override
  public double score(int sourceId, Sequence<TK> source,
      List<Sequence<TK>> references, Sequence<TK> translation) {
    
    double score = BLEUMetric.computeLocalSmoothScore(translation, references, order, doNakov);

    if (scaleLength) {
      // Take the min reference length
      int minLength = Integer.MAX_VALUE;
      for (Sequence<TK> sentence : references) {
        if (sentence.size() < minLength) {
          minLength = sentence.size();
        }
      }
      // Scale the score by the min reference length
      score *= minLength;
    }
    return score;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {}

  @Override
  public boolean isThreadsafe() {
    return true;
  }
}
