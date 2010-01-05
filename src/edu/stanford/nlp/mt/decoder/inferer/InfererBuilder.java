package mt.decoder.inferer;

import mt.decoder.feat.CombinedFeaturizer;
import mt.decoder.h.SearchHeuristic;
import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.util.Hypothesis;
import mt.decoder.util.PhraseGenerator;
import mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public interface InfererBuilder<TK,FV> {
	/**
	 * 
	 */
	InfererBuilder<TK,FV> setPhraseGenerator(PhraseGenerator<TK> phraseGenerator);
	
	/**
	 * 
	 */
	InfererBuilder<TK,FV> setIncrementalFeaturizer(CombinedFeaturizer<TK,FV> featurizer);
	
	/**
	 * 
	 */
	InfererBuilder<TK,FV> setScorer(Scorer<FV> scorer);
	
	/**
	 * 
	 */
	InfererBuilder<TK,FV> setSearchHeuristic(SearchHeuristic<TK,FV> heuristic);
	
	/**
	 * 
	 */
	InfererBuilder<TK,FV> setRecombinationFilter(RecombinationFilter<Hypothesis<TK,FV>> recombinationFilter);
	
	/**
	 * 
	 */
	Inferer<TK,FV> build();
}
