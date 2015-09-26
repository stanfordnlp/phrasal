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

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<TK, String> f) {
    final int size = f.targetPhrase.size();
    return (size != 0 && f.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME)) ?
      Collections.singletonList(new FeatureValue<>(FEATURE_NAME, -1.0 * size, true)) : null;
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
