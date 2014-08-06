package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;

/**
 * Word penalty feature: count of the target side of a rule.
 * 
 * @author danielcer
 * 
 */
public class WordPenaltyFeaturizer<TK> implements
    RuleFeaturizer<TK, String> {

  public static final String FEATURE_NAME = "WordPenalty";
  private static final double MOSES_WORD_PENALTY_MUL = -1.0;

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<TK, String> f) {
    if (f.targetPhrase == null || f.targetPhrase.size() == 0) {
      return null;
    } else {
      return FeatureUtils.wrapFeature(new FeatureValue<String>(FEATURE_NAME, MOSES_WORD_PENALTY_MUL
          * f.targetPhrase.size(), true));
    }
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
