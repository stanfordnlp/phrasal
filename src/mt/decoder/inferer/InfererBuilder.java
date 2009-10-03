package mt.decoder.inferer;

import mt.decoder.feat.IncrementalFeaturizer;
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
	 * @return
	 */
	InfererBuilder<TK,FV> setPhraseGenerator(PhraseGenerator<TK> phraseGenerator);
	
	/**
	 * 
	 * @param featurizer
	 * @return
	 */
	InfererBuilder<TK,FV> setIncrementalFeaturizer(CombinedFeaturizer<TK,FV> featurizer);
	
	/**
	 * 
	 * @param scorer
	 * @return
	 */
	InfererBuilder<TK,FV> setScorer(Scorer<FV> scorer);
	
	/**
	 * 
	 * @param heuristic
	 * @return
	 */
	InfererBuilder<TK,FV> setSearchHeuristic(SearchHeuristic<TK,FV> heuristic);
	
	/**
	 * 
	 * @param recombinationFilter
	 * @return
	 */
	InfererBuilder<TK,FV> setRecombinationFilter(RecombinationFilter<Hypothesis<TK,FV>> recombinationFilter);
	
	/**
	 * 
	 * @return
	 */
	Inferer<TK,FV> build();
}
