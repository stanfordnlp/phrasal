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
	 * @param phraseGenerator
	 */
	InfererBuilder<TK,FV> setPhraseGenerator(PhraseGenerator<TK> phraseGenerator);
	
	/**
	 * 
	 * @param featurizer
	 */
	InfererBuilder<TK,FV> setIncrementalFeaturizer(CombinedFeaturizer<TK,FV> featurizer);
	
	/**
	 * 
	 * @param scorer
	 */
	InfererBuilder<TK,FV> setScorer(Scorer<FV> scorer);
	
	/**
	 * 
	 * @param heuristic
	 */
	InfererBuilder<TK,FV> setSearchHeuristic(SearchHeuristic<TK,FV> heuristic);
	
	/**
	 * 
	 * @param recombinationFilter
	 */
	InfererBuilder<TK,FV> setRecombinationFilter(RecombinationFilter<Hypothesis<TK,FV>> recombinationFilter);
	
	/**
	 * 
	 */
	Inferer<TK,FV> build();
}
