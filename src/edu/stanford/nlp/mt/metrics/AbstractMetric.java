package mt.metrics;

import java.util.List;

import mt.base.ScoredFeaturizedTranslation;
import mt.metrics.IncrementalEvaluationMetric;

abstract public class AbstractMetric<TK,FV> implements EvaluationMetric<TK,FV> {

	@Override
	public double score(List<ScoredFeaturizedTranslation<TK,FV>> translations) {
		IncrementalEvaluationMetric<TK,FV> incMetric = getIncrementalMetric();
		for (ScoredFeaturizedTranslation<TK, FV> tran : translations) {
			incMetric.add(tran);
		}
		return incMetric.score();
	}

	@Override
	abstract public IncrementalEvaluationMetric<TK,FV> getIncrementalMetric();
}
