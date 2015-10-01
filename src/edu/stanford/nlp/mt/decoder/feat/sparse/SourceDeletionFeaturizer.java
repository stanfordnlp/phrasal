package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * The percentage of unaligned source words relative to the source.
 * 
 * @author Spence Green
 *
 */
public class SourceDeletionFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "SDL";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    int numUnaligned = 0;
    final int[][] s2t = f.rule.abstractRule.alignment.s2t();
    for (int i = 0; i < s2t.length; ++i) {
      if (s2t[i] == null || s2t[i].length == 0) ++numUnaligned;
    }
    final double ratio = (double) numUnaligned / (double) f.sourceSentence.size();
    return Collections.singletonList(new FeatureValue<>(FEATURE_NAME, ratio));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
