package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Returns the fertility of the rule.
 * 
 * @author Spence Green
 *
 */
public class RuleFertilityFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "FRT";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    int fertility = Math.abs(f.targetPhrase.size() - f.sourcePhrase.size());
    return fertility > 0 ? Collections.singletonList(new FeatureValue<>(FEATURE_NAME, Math.log(fertility)))
        : null;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
