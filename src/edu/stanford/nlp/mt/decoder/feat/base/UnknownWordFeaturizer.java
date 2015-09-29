package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;

/**
 * Unknown word feature.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <TK>
 */
public class UnknownWordFeaturizer<TK> implements RuleFeaturizer<TK, String> {

  public static final String FEATURE_NAME = "UnknownWord";
  private static final List<FeatureValue<String>> FEATURE = 
      Collections.singletonList(new FeatureValue<>(FEATURE_NAME, -1.0, true));
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<TK, String> f) {
    return f.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME) ? 
        FEATURE : null;
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
