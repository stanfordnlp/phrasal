package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.TranslationModel;

/**
 * An abstract factory for an inference algorithm.
 * 
 * @author danielcer
 * 
 */
abstract public class AbstractInfererBuilder<TK, FV> implements InfererBuilder<TK, FV> {
  protected FeatureExtractor<TK, FV> incrementalFeaturizer;
  protected TranslationModel<TK,FV> phraseGenerator;
  protected TranslationModel<TK,FV> foregroundModel = null;
  protected TranslationModel<TK,FV> termbaseModel = null;
  protected Scorer<FV> scorer;
  protected SearchHeuristic<TK, FV> heuristic;
  protected RecombinationFilter<Derivation<TK, FV>> filter;
  protected boolean filterUnknownWords;
  protected TranslationModel<TK, FV> unknownWordModel;

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
      TranslationModel<TK,FV> phraseGenerator) {
    this.phraseGenerator = phraseGenerator;
    return this;
  }
  
  public InfererBuilder<TK, FV> setForegroundModel(
      TranslationModel<TK,FV> foregroundModel) {
    this.foregroundModel = foregroundModel;
    return this;
  }

  public InfererBuilder<TK, FV> setTermbaseModel(
      TranslationModel<TK,FV> termbaseModel) {
    this.termbaseModel = termbaseModel;
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
  public InfererBuilder<TK, FV> setUnknownWordModel(TranslationModel<TK, FV> unknownWordModel, boolean filterUnknownWords) {
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
