package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Source dimension of the rule.
 * 
 * @author Spence Green
 *
 */
public class SourceRuleDimension implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "SRCD";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(String.format("%s:%d",FEATURE_NAME, f.sourcePhrase.size()), 1.0));
    return features;
  }

  @Override
  public boolean constructInternalAlignments() {
    return false;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
