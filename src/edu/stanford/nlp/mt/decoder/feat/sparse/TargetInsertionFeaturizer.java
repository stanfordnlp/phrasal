package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Target insertion ratio.
 * 
 * @author Spence Green
 *
 */
public class TargetInsertionFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "TNS";

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    int numInserted = 0;
    for (int i = 0; i < f.targetPhrase.size(); ++i) {
      int[] t2s = f.rule.abstractRule.alignment.t2s(i);
      if (t2s == null || t2s.length == 0) ++numInserted;
    }
    final double ratio = (double) numInserted / (double) f.sourceSentence.size();
    return Collections.singletonList(new FeatureValue<>(FEATURE_NAME, ratio));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
