package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Unknown word feature.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <TK>
 */
public class UnknownWordFeaturizer<TK> implements
    RuleFeaturizer<TK, String> {

  public static final String FEATURE_NAME = "UnknownWord";
  public static final double MOSES_UNKNOWN_WORD_MUL = -100.0;

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<TK, String> f) {
    final int size = f.targetPhrase.size();
    if (size != 0 && f.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME)) {
      List<FeatureValue<String>> features = Generics.newLinkedList();
      features.add(new FeatureValue<String>(FEATURE_NAME,
          MOSES_UNKNOWN_WORD_MUL * size));
      return features;
    }
    return null;
  }

  @Override
  public void initialize() {
  }
}
