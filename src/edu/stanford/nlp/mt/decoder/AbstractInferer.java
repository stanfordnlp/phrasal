package edu.stanford.nlp.mt.decoder;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.FeatureValueCollection;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * An abstract inference algorithm.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractInferer<TK, FV> implements Inferer<TK, FV> {
  protected final CombinedFeaturizer<TK, FV> featurizer;
  protected final PhraseGenerator<TK,FV> phraseGenerator;
  protected final Scorer<FV> scorer;
  protected final SearchHeuristic<TK, FV> heuristic;
  protected final RecombinationFilter<Derivation<TK, FV>> filter;
  protected final boolean filterUnknownWords;

  protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
    featurizer = builder.incrementalFeaturizer;
    phraseGenerator = builder.phraseGenerator;
    scorer = builder.scorer;
    heuristic = builder.heuristic;
    filter = builder.filter;
    filterUnknownWords = builder.filterUnknownWords;
  }

  protected AbstractInferer(AbstractInferer<TK, FV> inferer) {
    featurizer = inferer.featurizer;
    phraseGenerator = inferer.phraseGenerator;
    scorer = inferer.scorer;
    heuristic = inferer.heuristic;
    filter = inferer.filter;
    filterUnknownWords = inferer.filterUnknownWords;
  }

  protected FeatureValueCollection<FV> collectFeatureValues(
      Derivation<TK, FV> hyp) {
    class LinkedFeatureValues<FV2> extends LinkedList<FeatureValue<FV2>>
        implements FeatureValueCollection<FV2> {
      private static final long serialVersionUID = 1L;

      @Override
      public Object clone() {
        return super.clone();
      }
    }
    LinkedFeatureValues<FV> features = new LinkedFeatureValues<FV>();
    for (; hyp != null; hyp = hyp.preceedingDerivation) {
      List<FeatureValue<FV>> localFeatures = hyp.localFeatures;
      if (localFeatures != null) {
        features.addAll(localFeatures);
      }
    }
    return features;
  }
}
