package edu.stanford.nlp.mt.decoder;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.annotators.Annotator;
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
 */
abstract public class AbstractInfererBuilder<TK, FV> implements
    InfererBuilder<TK, FV> {
  public List<Annotator<TK,FV>> annotators = new ArrayList<Annotator<TK,FV>>();  
  CombinedFeaturizer<TK, FV> incrementalFeaturizer;
  PhraseGenerator<TK,FV> phraseGenerator;
  Scorer<FV> scorer;
  SearchHeuristic<TK, FV> heuristic;
  RecombinationFilter<Derivation<TK, FV>> filter;

  @Override
  abstract public Inferer<TK, FV> build();

  @Override
  public InfererBuilder<TK, FV> setAnnotators(List<Annotator<TK,FV>> annotators) {
	 this.annotators = new ArrayList<Annotator<TK,FV>>(annotators);
     return this;
  }
  
  @Override
  public InfererBuilder<TK, FV> setIncrementalFeaturizer(
      CombinedFeaturizer<TK, FV> featurizer) {
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
  public InfererBuilder<TK, FV> setRecombinationFilter(
      RecombinationFilter<Derivation<TK, FV>> recombinationFilter) {
    this.filter = recombinationFilter;
    return this;
  }

}
