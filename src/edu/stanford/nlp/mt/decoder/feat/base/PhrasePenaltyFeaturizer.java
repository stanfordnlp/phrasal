package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Collections;
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
  public static final String FEATURE_NAME = "PhrasePenalty";

  // Cache since this value will simply be aggregated by the feature API
  private static final List<FeatureValue<String>> features;
  static {
    List<FeatureValue<String>> feats = Generics.newArrayList(1);
    feats.add(new FeatureValue<String>(FEATURE_NAME, 1.0, true));
    features = Collections.unmodifiableList(feats);
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
