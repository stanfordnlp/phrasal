package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.TranslationModel;

/**
 * Configure an Inferer, which is an instance of a search procedure.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public interface InfererBuilder<TK, FV> {

  /**
   * Set the translation model.
   * 
   * @param phraseGenerator
   * @return
   */
  InfererBuilder<TK, FV> setPhraseGenerator(TranslationModel<TK,FV> phraseGenerator);

  /**
   * Set the featurizer.
   * 
   * @param featurizer
   * @return
   */
  InfererBuilder<TK, FV> setFeaturizer(FeatureExtractor<TK, FV> featurizer);

  /**
   * Set the model scorer.
   * 
   * @param scorer
   * @return
   */
  InfererBuilder<TK, FV> setScorer(Scorer<FV> scorer);

  /**
   * Set the future cost heuristic.
   * 
   * @param heuristic
   * @return
   */
  InfererBuilder<TK, FV> setSearchHeuristic(SearchHeuristic<TK, FV> heuristic);

  /**
   * Set the recombination policy.
   * 
   * @param recombinationFilter
   * @return
   */
  InfererBuilder<TK, FV> setRecombinationFilter(
      RecombinationFilter<Derivation<TK, FV>> recombinationFilter);

  /**
   * Set the unknown word policy.
   * 
   * @param unknownWordModel The unknown word model
   * @param filterUnknownWords if true, then remove unknown words from source. Use unknownWordModel otherwise.
   * @return
   */
  InfererBuilder<TK, FV> setUnknownWordModel(TranslationModel<TK, FV> unknownWordModel, 
      boolean filterUnknownWords);

  /**
   * Get a new Inferer instance.
   * 
   * @return
   */
  Inferer<TK, FV> newInferer();
}
