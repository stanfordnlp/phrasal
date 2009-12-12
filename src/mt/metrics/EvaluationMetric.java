package mt.metrics;

import java.util.*;

import mt.base.NBestListContainer;
import mt.base.ScoredFeaturizedTranslation;
import mt.decoder.recomb.RecombinationFilter;
import mt.metrics.IncrementalEvaluationMetric;


/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface EvaluationMetric<TK,FV>  {
	
	/**
	 * 
	 */
	double score(List<ScoredFeaturizedTranslation<TK,FV>> sequences);
	
	/**
	 * 
	 */
	IncrementalEvaluationMetric<TK,FV> getIncrementalMetric();

	/**
	 * 
	 */
	public IncrementalEvaluationMetric<TK,FV> getIncrementalMetric(NBestListContainer<TK, FV> nbestList);
		
	/**
	 * 
	 */
	public RecombinationFilter<IncrementalEvaluationMetric<TK,FV>> getIncrementalMetricRecombinationFilter();
	
	/**
	 * 
	 */
	double maxScore();
}
