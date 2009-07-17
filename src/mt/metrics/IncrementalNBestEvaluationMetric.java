package mt.metrics;

import mt.base.ScoredFeaturizedTranslation;

/**
 * @author Michel Galley
 */
public interface IncrementalNBestEvaluationMetric<TK,FV> extends IncrementalEvaluationMetric<TK,FV> {

  /**
	 *
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> add(int nbestId, ScoredFeaturizedTranslation<TK,FV> trans);

  /**
	 *
	 * @param index
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> replace(int index, int nbestId, ScoredFeaturizedTranslation<TK,FV> trans);

}
