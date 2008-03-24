package mt;

import java.util.List;

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
