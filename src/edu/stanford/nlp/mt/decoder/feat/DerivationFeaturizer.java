package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Extract features from partial derivations. The featurizer is called each
 * time a new rule is applied to a derivation.  initialize()
 * is called once on a new sentence.  Then, each time a derivation 
 * is extended with a new rule application, featurize is called.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public interface DerivationFeaturizer<TK, FV> extends Featurizer<TK,FV> {
  
  /**
   * This call is made *before* decoding a new input begins.
   * 
   * @param sourceInputId
   * @param ruleList
   * @param source
   */
  void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source);

  /**
   * Extract and return a list of features. If features overlap in the list, 
   * their values will be added.
   * 
   * @return a list of features or null.
   */
  List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f);
}
