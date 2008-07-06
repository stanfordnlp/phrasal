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
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> add(ScoredFeaturizedTranslation<TK,FV> trans);
	
	/**
	 * 
	 * @param index
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> replace(int index, ScoredFeaturizedTranslation<TK,FV> trans);
	
	/**
	 * 
	 * @return
	 */
	double score();
	
	/**
	 * 
	 * @return
	 */
	double maxScore();
	
	/**
	 * 
	 * @return
	 */
	int size();
	
	/**
	 * 
	 * @return
	 */
	public IncrementalEvaluationMetric<TK,FV> clone();
}
