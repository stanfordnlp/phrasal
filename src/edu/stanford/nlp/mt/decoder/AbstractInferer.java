package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.TranslationModel;

/**
 * An abstract inference algorithm.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractInferer<TK, FV> implements Inferer<TK, FV> {
  public final FeatureExtractor<TK, FV> featurizer;
  public final TranslationModel<TK,FV> phraseGenerator;
  public final Scorer<FV> scorer;
  protected final SearchHeuristic<TK, FV> heuristic;
  protected final RecombinationFilter<Derivation<TK, FV>> filter;
  protected final boolean filterUnknownWords;
  protected final TranslationModel<TK,FV> unknownWordModel;

  /**
   * Constructor.
   * 
   * @param builder
   */
  protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
    featurizer = builder.incrementalFeaturizer;
    phraseGenerator = builder.phraseGenerator;
    scorer = builder.scorer;
    heuristic = builder.heuristic;
    filter = builder.filter;
    filterUnknownWords = builder.filterUnknownWords;
    unknownWordModel = builder.unknownWordModel;
  }

  /**
   * Constructor.
   * 
   * @param inferer
   */
  protected AbstractInferer(AbstractInferer<TK, FV> inferer) {
    featurizer = inferer.featurizer;
    phraseGenerator = inferer.phraseGenerator;
    scorer = inferer.scorer;
    heuristic = inferer.heuristic;
    filter = inferer.filter;
    filterUnknownWords = inferer.filterUnknownWords;
    unknownWordModel = inferer.unknownWordModel;
  }
}
