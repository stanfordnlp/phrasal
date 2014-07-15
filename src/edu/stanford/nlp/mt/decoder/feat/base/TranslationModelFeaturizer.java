package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;

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
  private static final FeatureValue<String> emptyFV = new FeatureValue<String>(
      "", 0.0);

  // Only construct the feature strings once for each phrase table
  private final ConcurrentHashMap<String, String[]> featureNamesHash;
  private final int numFeatures;

  private String[] createAndCacheFeatureNames(String phraseTableName, String[] phraseScoreNames) {
    String[] featureNames = new String[phraseScoreNames.length];
    for (int i = 0; i < featureNames.length; i++) {
      if (phraseScoreNames[i] != null) {
        featureNames[i] = String.format("%s:%s", FEATURE_PREFIX, phraseScoreNames[i]);
      }
    }
    featureNamesHash.putIfAbsent(phraseTableName, featureNames);
    return featureNames;
  }

  /**
   * Constructor.
   * 
   * @param numFeatures Set to <code>Integer.MAX_VALUE</code> to specify
   * all features defined in the phrase table.
   */
  public TranslationModelFeaturizer(int numFeatures) {
    this.featureNamesHash = new ConcurrentHashMap<String,String[]>();
    this.numFeatures = numFeatures;
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
    String[] featureNames = featureNamesHash.containsKey(phraseTableName) ? 
        featureNamesHash.get(phraseTableName) : 
          createAndCacheFeatureNames(phraseTableName, featurizable.phraseScoreNames);

    // construct array of FeatureValue objects
    List<FeatureValue<String>> features = Generics.newLinkedList();
    final int numEffectiveFeatures = Math.min(this.numFeatures, featureNames.length);
    for (int i = 0; i < numEffectiveFeatures; i++) {
      features.add((i < featurizable.translationScores.length) ? new FeatureValue<String>(
          featureNames[i], featurizable.translationScores[i]) : emptyFV);
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
