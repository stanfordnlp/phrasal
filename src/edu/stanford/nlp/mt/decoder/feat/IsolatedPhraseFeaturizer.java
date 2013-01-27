package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
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
	 * 
	 */
  List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f);

  /**
	 * 
	 */
  FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f);
}
