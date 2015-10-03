package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Length ratio "trait" feature from Devlin et al. (2012).
 * 
 * @author Spence Green
 *
 */
public class LengthRatio implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "LRA";
  
  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    double ratio = (double) f.targetPhrase.size() / (double) f.sourceSentence.size();
    return Collections.singletonList(new FeatureValue<>(FEATURE_NAME, ratio));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
