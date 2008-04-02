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
	 * @param sequences
	 * @return
	 */
	double score(List<ScoredFeaturizedTranslation<TK,FV>> sequences);
	
	/**
	 * 
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> getIncrementalMetric();

	/**
	 * 
	 * @param nbestList
	 * @return
	 */
	public IncrementalEvaluationMetric<TK,FV> getIncrementalMetric(NBestListContainer<TK, FV> nbestList);
		
	/**
	 * 
	 * @return
	 */
	public RecombinationFilter<IncrementalEvaluationMetric<TK,FV>> getIncrementalMetricRecombinationFilter();
	
	/**
	 * 
	 * @return
	 */
	double maxScore();
}
