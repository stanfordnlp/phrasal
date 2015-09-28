package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
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

  // Only construct the feature strings once for each phrase table
  private final ConcurrentHashMap<String, String[]> featureNamesHash;

  
  /**
   * Constructor.
   * 
   */
  public TranslationModelFeaturizer() {
    this.featureNamesHash = new ConcurrentHashMap<>();
  }
  
  /**
   * Convert raw TM feature names to the featurized format.
   * 
   * @param featureName
   * @return
   */
  public static String toTMFeature(String featureName) {
    return String.format("%s:%s", FEATURE_PREFIX, featureName);
  }
  
  /**
   * Add a prefix to the feature names.
   * 
   * @param phraseTableName
   * @param phraseScoreNames
   * @return
   */
  private String[] createAndCacheFeatureNames(String phraseTableName, String[] phraseScoreNames, boolean forceUpdate) {
    String[] featureNames = Arrays.stream(phraseScoreNames).map(s -> toTMFeature(s)).toArray(String[]::new);
    if (forceUpdate) featureNamesHash.put(phraseTableName, featureNames);
    else featureNamesHash.putIfAbsent(phraseTableName, featureNames);
    return featureNames;
  }
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> featurizable) {
    final String phraseTableName = featurizable.phraseTableName;
    if (featurizable.phraseScoreNames.length != featurizable.translationScores.length) {
      throw new RuntimeException("Score name/value arrays of different dimensions for table: " + phraseTableName);
    }
    
    String[] featureNames = featureNamesHash.get(phraseTableName);
    if (featureNames == null) {
      featureNames = createAndCacheFeatureNames(phraseTableName, featurizable.phraseScoreNames, false);
    }
    if (featureNames.length < featurizable.translationScores.length) {
      // Synthetic rules can have different numbers of features.
      featureNames = Arrays.copyOf(featureNames, featurizable.translationScores.length);
    } else if (featureNames.length > featurizable.translationScores.length) {
      // We want to cache the longest feature list for each phrase table
      featureNames = createAndCacheFeatureNames(phraseTableName, featurizable.phraseScoreNames, true);
    }
    
    // construct array of FeatureValue objects
    final List<FeatureValue<String>> features = new ArrayList<>(featureNames.length);
    for (int i = 0; i < featureNames.length; ++i) {
      features.add(new FeatureValue<>(featureNames[i], featurizable.translationScores[i], true));
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
