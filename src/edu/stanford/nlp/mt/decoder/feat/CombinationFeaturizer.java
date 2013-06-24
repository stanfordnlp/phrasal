package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteRule;
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
 * <br>
 * IncrementalFeaturizers will not be called in a reentrant manner.
 * Information calculated during <code>initialize</code> can be stored
 * directly in the Featurizer.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public interface CombinationFeaturizer<TK, FV> extends Featurizer<TK,FV> {
  
  /**
   * This call is made *before* decoding a new input begins.
   * @param sourceInputId TODO
   * @param options
   * @param foreign
   * @param featureIndex 
   */
  void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> options, Sequence<TK> foreign, Index<String> featureIndex);

  /**
   * This call is made *after* decoding an input ends.
   */
  void reset();

  /**
   * Return a list of features or null.  
   * <br>
   * If features overlap in the list, their values will be added.
   */
  List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f);

  /**
   * Return a single feature or null. 
   */
  FeatureValue<FV> featurize(Featurizable<TK, FV> f);
}
