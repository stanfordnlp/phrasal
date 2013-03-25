package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Extract features from hypotheses. The featurizer is called each
 * time a new rule is applied to a partial hypothesis.  initialize()
 * is called once on a new sentence.  Then, each time the hypothesis
 * is extended with a new rule application, both listFeaturize and
 * featurize are called.  Only one of these should return non-null.
 * Features should have unique prefixes so that the Featurizer does
 * not conflict with any other Featurizer.
 * 
 * @author danielcer
 * @author Spence Green
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
  void initialize(List<ConcreteTranslationOption<TK,FV>> options,
      Sequence<TK> foreign, Index<String> featureIndex);

  /**
   * Reset featurizer state.
   */
  void reset();

  /**
	 * Return a list of features or null.
	 */
  List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f);

  /**
	 * Return a single feature or null. 
	 */
  FeatureValue<FV> featurize(Featurizable<TK, FV> f);
}
