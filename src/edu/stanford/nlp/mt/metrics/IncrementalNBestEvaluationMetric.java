package mt.metrics;

import mt.base.ScoredFeaturizedTranslation;

/**
 * @author Michel Galley
 */
public interface IncrementalNBestEvaluationMetric<TK,FV> extends IncrementalEvaluationMetric<TK,FV> {

  /**
	 *
	 */
	IncrementalEvaluationMetric<TK,FV> add(int nbestId, ScoredFeaturizedTranslation<TK,FV> trans);

  /**
	 *
	 */
	IncrementalEvaluationMetric<TK,FV> replace(int index, int nbestId, ScoredFeaturizedTranslation<TK,FV> trans);

}
