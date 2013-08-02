package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.Counter;

/**
 * Training algorithms that update the model weights with each training example.
 * 
 * @author Spence Green
 *
 */
public interface OnlineOptimizer<TK,FV> {
  /**
   * Compute the gradient for a given training example.
   * 
   * @param weights 
   * @param weights
   * @param source
   * @param sourceId
   * @param translations
   * @param referenceWeights
   * @param scoreMetric
   * @param featureWhitelist 
   * @return Updated weight vector.
   */
  public Counter<FV> getGradient(Counter<FV> weights, 
      Sequence<TK> source, 
      int sourceId,
      List<RichTranslation<TK, FV>> translations,
      List<Sequence<TK>> references, 
      double[] referenceWeights, 
      SentenceLevelMetric<TK,FV> scoreMetric);
  
  /**
   * Compute the gradient for a mini-batch.
   * 
   * @param weights
   * @param sources
   * @param sourceIds
   * @param translations
   * @param references
   * @param referenceWeights TODO
   * @param scoreMetric
   * @return
   */
  public Counter<FV> getBatchGradient(Counter<FV> weights, 
      List<Sequence<TK>> sources, 
      int[] sourceIds,
      List<List<RichTranslation<TK, FV>>> translations,
      List<List<Sequence<TK>>> references, 
      double[] referenceWeights, 
      SentenceLevelMetric<TK,FV> scoreMetric);
  
  /**
   * Return a new updater object, which defines the online update rule. The updater
   * should configure the gain schedule, if any.
   * 
   * @return
   */
  public OnlineUpdateRule<FV> newUpdater();
}
