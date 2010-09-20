package edu.stanford.nlp.mt.decoder.inferer;

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
 */
abstract public class AbstractInfererBuilder<TK, FV> implements
    InfererBuilder<TK, FV> {

  CombinedFeaturizer<TK, FV> incrementalFeaturizer;
  PhraseGenerator<TK> phraseGenerator;
  Scorer<FV> scorer;
  SearchHeuristic<TK, FV> heuristic;
  RecombinationFilter<Hypothesis<TK, FV>> filter;

  @Override
  abstract public Inferer<TK, FV> build();

  @Override
  public InfererBuilder<TK, FV> setIncrementalFeaturizer(
      CombinedFeaturizer<TK, FV> featurizer) {
    this.incrementalFeaturizer = featurizer;

    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setPhraseGenerator(
      PhraseGenerator<TK> phraseGenerator) {
    this.phraseGenerator = phraseGenerator;
    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setScorer(Scorer<FV> scorer) {
    this.scorer = scorer;
    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setSearchHeuristic(
      SearchHeuristic<TK, FV> heuristic) {
    this.heuristic = heuristic;
    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setRecombinationFilter(
      RecombinationFilter<Hypothesis<TK, FV>> recombinationFilter) {
    this.filter = recombinationFilter;
    return this;
  }

}
