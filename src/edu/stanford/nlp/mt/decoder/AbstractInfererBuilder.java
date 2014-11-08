package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.PhraseGenerator;

/**
 * An abstract factory for an inference algorithm.
 * 
 * @author danielcer
 * 
 */
abstract public class AbstractInfererBuilder<TK, FV> implements InfererBuilder<TK, FV> {
  protected FeatureExtractor<TK, FV> incrementalFeaturizer;
  protected PhraseGenerator<TK,FV> phraseGenerator;
  protected Scorer<FV> scorer;
  protected SearchHeuristic<TK, FV> heuristic;
  protected RecombinationFilter<Derivation<TK, FV>> filter;
  protected boolean filterUnknownWords;
  protected PhraseGenerator<TK, FV> unknownWordModel;

  @Override
  abstract public Inferer<TK, FV> newInferer();

  @Override
  public InfererBuilder<TK, FV> setFeaturizer(
      FeatureExtractor<TK, FV> featurizer) {
    this.incrementalFeaturizer = featurizer;

    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setPhraseGenerator(
      PhraseGenerator<TK,FV> phraseGenerator) {
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
  public InfererBuilder<TK, FV> setUnknownWordModel(PhraseGenerator<TK, FV> unknownWordModel, boolean filterUnknownWords) {
    this.unknownWordModel = unknownWordModel;
    this.filterUnknownWords = filterUnknownWords;
    return this;
  }

  @Override
  public InfererBuilder<TK, FV> setRecombinationFilter(
      RecombinationFilter<Derivation<TK, FV>> recombinationFilter) {
    this.filter = recombinationFilter;
    return this;
  }

}
