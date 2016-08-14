package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;


/**
 * Source dimension of the rule.
 * 
 * @author Spence Green
 *
 */
public class RuleSourceDimension implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "SRCD";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    String featureString = String.format("%s:%d",FEATURE_NAME, f.sourcePhrase.size());
    return Collections.singletonList(new FeatureValue<>(featureString, 1.0));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
