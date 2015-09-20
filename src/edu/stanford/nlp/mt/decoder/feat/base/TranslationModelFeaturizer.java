package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Dense phrase table / rule feature extractor.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public class TranslationModelFeaturizer implements RuleFeaturizer<IString, String> {
  
  public static final String FEATURE_PREFIX = "TM";

  /**
   * Constructor.
   * 
   */
  public TranslationModelFeaturizer() {}
  
  /**
   * Convert raw TM feature names to the featurized format.
   * 
   * @param featureName
   * @return
   */
  public static String toTMFeature(String featureName) {
    return String.format("%s:%s", FEATURE_PREFIX, featureName);
  }
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> featurizable) {
    if (featurizable.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME)) {
      // Don't score synthetic rules from the OOV model
      return null;
    }

    // lookup/construct the list of feature names
    final String phraseTableName = featurizable.phraseTableName;
    assert featurizable.phraseScoreNames.length == featurizable.translationScores.length :
      "Score name/value arrays of different dimensions for table: " + phraseTableName;
    
    final String[] featureNames = featurizable.phraseScoreNames;
    final List<FeatureValue<String>> features = new ArrayList<>(featureNames.length);
    for (int i = 0; i < featureNames.length; ++i) {
      features.add(new FeatureValue<>(toTMFeature(featureNames[i]), featurizable.translationScores[i], true));
    }
    return features;
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
