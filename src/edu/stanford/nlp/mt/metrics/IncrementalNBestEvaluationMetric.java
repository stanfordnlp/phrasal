package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * @author Michel Galley
 */
public interface IncrementalNBestEvaluationMetric<TK, FV> extends
    IncrementalEvaluationMetric<TK, FV> {

  /**
	 *
	 */
  IncrementalEvaluationMetric<TK, FV> add(int nbestId,
      ScoredFeaturizedTranslation<TK, FV> trans);
  
  IncrementalEvaluationMetric<TK, FV> add(int nbestId,
      Sequence<TK> trans);

  /**
	 *
	 */
  IncrementalEvaluationMetric<TK, FV> replace(int index, int nbestId,
      ScoredFeaturizedTranslation<TK, FV> trans);

}
