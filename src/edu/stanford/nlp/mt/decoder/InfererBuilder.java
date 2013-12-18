package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public interface InfererBuilder<TK, FV> {
  /**
	 * 
	 */
  InfererBuilder<TK, FV> setPhraseGenerator(PhraseGenerator<TK,FV> phraseGenerator);

  /**
	 * 
	 */
  InfererBuilder<TK, FV> setIncrementalFeaturizer(
      CombinedFeaturizer<TK, FV> featurizer);

  /**
	 * 
	 */
  InfererBuilder<TK, FV> setScorer(Scorer<FV> scorer);

  /**
	 * 
	 */
  InfererBuilder<TK, FV> setSearchHeuristic(SearchHeuristic<TK, FV> heuristic);

  /**
	 * 
	 */
  InfererBuilder<TK, FV> setRecombinationFilter(
      RecombinationFilter<Derivation<TK, FV>> recombinationFilter);

  /**
   * 
   * @param filterUnknownWords
   * @return
   */
  InfererBuilder<TK, FV> setFilterUnknownWords(boolean filterUnknownWords);
  
  /**
	 * 
	 */
  Inferer<TK, FV> build();
}
