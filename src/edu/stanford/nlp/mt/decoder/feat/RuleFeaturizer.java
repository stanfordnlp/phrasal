package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;

/**
 * Extract features from a translation rule (phrase pair) independent of a derivation.
 * 
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public interface RuleFeaturizer<TK, FV> extends Featurizer<TK,FV> {
  
  /**
   * This call is made *before* decoding with a rule. Do any setup here.
   */
  void initialize();
  
  /**
   * Extract and return features for <code>f.rule</code>. If features overlap 
   * in the list, their values will be added.
   * 
	 * @return a list of features or null.
	 */
  List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f);
  
  /** 
   * RuleFeaturizers that only contribute to the isolation score, which
   * is used by the future cost search heuristics, should implement this interface.
   * The feature values will not be cached and will thus not be scored when
   * the rule is applied in a <code>Derivation</code>.
   */
  boolean isolationScoreOnly();
}
