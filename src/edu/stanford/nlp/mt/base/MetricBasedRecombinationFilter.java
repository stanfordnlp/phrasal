package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.MultiTranslationState;
import edu.stanford.nlp.mt.metrics.*;

public class MetricBasedRecombinationFilter<TK,FV> implements RecombinationFilter<MultiTranslationState<TK, FV>> {
	RecombinationFilter<IncrementalEvaluationMetric<TK,FV>> metricFilter;
	
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
	public MetricBasedRecombinationFilter(EvaluationMetric<TK,FV> metric) {
		metricFilter = metric.getIncrementalMetricRecombinationFilter();
	}
	
	@Override
	public boolean combinable(MultiTranslationState<TK, FV> hypA,
			MultiTranslationState<TK, FV> hypB) {
		return metricFilter.combinable(hypA.incMetric, hypB.incMetric);
	}

	@Override
	public long recombinationHashCode(MultiTranslationState<TK, FV> hyp) {
		return metricFilter.recombinationHashCode(hyp.incMetric);
	}
	
}
