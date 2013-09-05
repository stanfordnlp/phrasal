package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * The target dimension of the rule.
 * 
 * @author Spence Green
 *
 */
public class TargetRuleDimension implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTD";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(String.format("%s:%d",FEATURE_NAME, f.targetPhrase.size()), 1.0));
    return features;
  }
}
