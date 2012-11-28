package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.Counter;

/**
 * Training algorithms that update the model with each training example.
 * 
 * @author Spence Green
 *
 */
public interface OnlineOptimizer<TK,FV> {

  /**
   * Update the model weights with a training example.
   * 
   * @param source
   * @param sourceId
   * @param translations
   * @param objective
   * @param weights
   * 
   * @return Updated weight vector.
   */
  public Counter<FV> update(Sequence<TK> source, 
      int sourceId, 
      List<RichTranslation<TK, FV>> translations,
      List<Sequence<TK>> references,
      SentenceLevelMetric<TK,FV> objective,
      Counter<FV> weights);
}
