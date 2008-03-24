package mt;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface IncrementalEvaluationMetric<TK,FV> extends State<IncrementalEvaluationMetric<TK,FV>>{
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	IncrementalEvaluationMetric<TK,FV> add(ScoredFeaturizedTranslation<TK,FV> trans);
	
	/**
	 * 
	 * @param index
	 * @param sequence
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
