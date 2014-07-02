package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;

public class PTMPhrasePenalty implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "PTMPhrasePenalty";
  
  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, 1.0));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }

}
