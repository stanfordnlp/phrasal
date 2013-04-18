package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Index;

/**
 *
 * @author danielcer
 *
 * @param <TK>
 */
public class UnknownWordFeaturizer<TK> implements
    IncrementalFeaturizer<TK, String>, IsolatedPhraseFeaturizer<TK, String> {

  public static final String FEATURE_NAME = "UnknownWord";
  public static final String UNKNOWN_PHRASE_TAG = "unknownphrase";
  public static final String UNKNOWN_PHRASE_TABLE_NAME = "IdentityPhraseGenerator(Dyn)";
  public static final double MOSES_UNKNOWN_WORD_MUL = -100.0;

  @Override
  public FeatureValue<String> featurize(Featurizable<TK, String> f) {
    if (f.phraseScoreNames.length != 1)
      return null;
    if (f.phraseScoreNames[0] != UNKNOWN_PHRASE_TAG)
      return null;
    // if (f.phraseScoreNames.length != 1) return new
    // FeatureValue<String>(FEATURE_NAME, 0.0);
    // if (f.phraseScoreNames[0] != UNKNOWN_PHRASE_TAG) return new
    // FeatureValue<String>(FEATURE_NAME, 0.0);
    int size = f.targetPhrase.size();
    return (size == 0) ? null : new FeatureValue<String>(FEATURE_NAME,
        MOSES_UNKNOWN_WORD_MUL * size);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<TK, String> f) {
    return null;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<TK, String> f) {
    return featurize(f);
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<TK, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<TK,String>> options,
      Sequence<TK> foreign, Index<String> featureIndex) {
  }

  @Override
  public void reset() {
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
