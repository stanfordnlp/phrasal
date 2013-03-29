package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.util.Index;

/**
 * Extract features from a translation rule (phrase pair) before it is used in a derivation.
 * 
 * The decoder can cache these feature values during phrase table lookup for more efficient lattice
 * generation. To enable caching, return CacheableFeatureValue objects. Otherwise, the featurizer
 * needs to implement IncrementalFeaturizer so that the feature values are re-computed during
 * hypothesis generation.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public interface IsolatedPhraseFeaturizer<TK, FV> {
  
  /**
   * Initialize the featurizer.
   * 
   * @param featureIndex
   */
  void initialize(Index<String> featureIndex);
  
  /**
	 * Return a list of features or null.
	 */
  List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f);

  /**
	 * Return a single feature or null.
	 */
  FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f);
}
