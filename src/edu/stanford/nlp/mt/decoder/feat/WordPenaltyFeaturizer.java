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
 */
public class WordPenaltyFeaturizer<TK> implements
    IncrementalFeaturizer<TK, String>, IsolatedPhraseFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "WordPenaltyFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static final String FEATURE_NAME = "WordPenalty";
  static public final double MOSES_WORD_PENALTY_MUL = -1.0;

  @Override
  public FeatureValue<String> featurize(Featurizable<TK, String> f) {
    // if (f.translatedPhrase == null) return null;
    if (f.targetPhrase == null)
      return new FeatureValue<String>(FEATURE_NAME, 0.0);
    return new FeatureValue<String>(FEATURE_NAME, MOSES_WORD_PENALTY_MUL
        * f.targetPhrase.size());
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
