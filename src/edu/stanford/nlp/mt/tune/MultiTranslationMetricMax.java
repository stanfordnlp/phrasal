package edu.stanford.nlp.mt.tune;

import java.util.List;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;

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
	 */
	List<ScoredFeaturizedTranslation<TK,FV>>  maximize(NBestListContainer<TK,FV> nbest);
}
