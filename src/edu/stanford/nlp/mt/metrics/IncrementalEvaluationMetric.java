package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.State;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public interface IncrementalEvaluationMetric<TK, FV> extends
    State<IncrementalEvaluationMetric<TK, FV>>, Cloneable {
  /**
	 * 
	 */
  IncrementalEvaluationMetric<TK, FV> add(
      ScoredFeaturizedTranslation<TK, FV> trans);

  /**
	 * 
	 */
  IncrementalEvaluationMetric<TK, FV> replace(int index,
      ScoredFeaturizedTranslation<TK, FV> trans);

  /**
	 * 
	 */
  double score();

  /**
	 * 
	 */
  double maxScore();

  /**
	 * 
	 */
  int size();

  /**
	 * 
	 */
  public Object clone() throws CloneNotSupportedException;
}
