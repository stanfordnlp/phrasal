package mt.metrics;

import mt.base.ScoredFeaturizedTranslation;
import mt.decoder.util.State;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface IncrementalEvaluationMetric<TK,FV> extends State<IncrementalEvaluationMetric<TK,FV>>{
	/**
	 * 
	 */
	IncrementalEvaluationMetric<TK,FV> add(ScoredFeaturizedTranslation<TK,FV> trans);
	
	/**
	 * 
	 * @param index
	 */
	IncrementalEvaluationMetric<TK,FV> replace(int index, ScoredFeaturizedTranslation<TK,FV> trans);
	
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
	public IncrementalEvaluationMetric<TK,FV> clone();
}
