package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;


/**
 * The target dimension of the rule.
 * 
 * @author Spence Green
 *
 */
public class RuleTargetDimension implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTD";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    String featureString = String.format("%s:%d",FEATURE_NAME, f.targetPhrase.size());
    return Collections.singletonList(new FeatureValue<>(featureString, 1.0));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
