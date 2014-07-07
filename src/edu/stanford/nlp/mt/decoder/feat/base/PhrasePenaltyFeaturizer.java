package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.util.Generics;

/**
 * Moses phrase penalty generated here so that you don't have to read it
 * countless times from the phrase table.
 * 
 * @author Michel Galley
 * 
 * @param <TK>
 */
public class PhrasePenaltyFeaturizer<TK> implements
    RuleFeaturizer<TK, String> {
  private static String FEATURE_NAME = "PhrasePenalty";
  // mg2008: please don't change to "= 1" since not exactly the same value:
  private static final double FEATURE_VALUE = Math.log(2.718);

  // Cache since this value will simply be aggregated by the feature API
  private static final List<FeatureValue<String>> features = Generics.newArrayList(1);
  static {
    features.add(new FeatureValue<String>(FEATURE_NAME, FEATURE_VALUE));
  }
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<TK, String> f) {
    return features;
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
