package mt;

import java.util.List;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public interface MultiTranslationMetricMax<TK,FV> {
	
	/**
	 * 
	 * @param nbest
	 * @return
	 */
	List<ScoredFeaturizedTranslation<TK,FV>>  maximize(NBestListContainer<TK,FV> nbest);
}
