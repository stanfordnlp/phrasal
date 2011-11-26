package edu.stanford.nlp.mt.decoder.inferer;

import java.util.List;

import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
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
  InfererBuilder<TK, FV> setPhraseGenerator(PhraseGenerator<TK> phraseGenerator);

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
      RecombinationFilter<Hypothesis<TK, FV>> recombinationFilter);

  /**
   * 
   * @param annotators
   */
  InfererBuilder<TK, FV> setAnnotators(List<Annotator<TK>> annotators);
  /**
	 * 
	 */
  Inferer<TK, FV> build();
}
