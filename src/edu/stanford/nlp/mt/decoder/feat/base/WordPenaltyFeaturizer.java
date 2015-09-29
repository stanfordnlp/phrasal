package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;

/**
 * Word penalty feature: count of the target side of a rule.
 * 
 * @author danielcer
 * 
 */
public class WordPenaltyFeaturizer<TK> implements RuleFeaturizer<TK, String> {

  public static final String FEATURE_NAME = "WordPenalty";

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<TK, String> f) {
    return (f.targetPhrase == null || f.targetPhrase.size() == 0) ? null :
      Collections.singletonList(new FeatureValue<>(FEATURE_NAME, -1.0 * f.targetPhrase.size(), true));
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
