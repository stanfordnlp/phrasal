package mt.tune;

import java.util.List;

import mt.base.NBestListContainer;
import mt.base.ScoredFeaturizedTranslation;

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
	 */
	List<ScoredFeaturizedTranslation<TK,FV>>  maximize(NBestListContainer<TK,FV> nbest);
}
