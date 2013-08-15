package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;

/**
 * Extract features from a translation rule (phrase pair) before it is used in a derivation.
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
   * Extract and return features for <code>f.rule</code>.
   * 
	 * @return a list of features or null.
	 */
  List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f);
}
