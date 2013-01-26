package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Extract features from hypotheses as they are built incrementally.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public interface IncrementalFeaturizer<TK, FV> {
  
  /**
   * Initialize the featurizer.
   * 
   * @param options
   * @param foreign
   * @param featureIndex 
   */
  void initialize(List<ConcreteTranslationOption<TK>> options,
      Sequence<TK> foreign, Index<String> featureIndex);

  /**
   * Reset featurizer state.
   */
  void reset();

  /**
	 * Extract one or more features
	 */
  List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f);

  /**
	 * Extract one feature.
	 */
  FeatureValue<FV> featurize(Featurizable<TK, FV> f);
}
