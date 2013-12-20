package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Dense phrase table / rule feature extractor.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public class PhraseTableScoresFeaturizer<T> implements RuleFeaturizer<T, String> {
  private final static String PREFIX = "TM";
  private static final FeatureValue<String> emptyFV = new FeatureValue<String>(
      null, 0.0);

  // Only construct the feature strings once for each phrase table
  private final Map<String, String[]> featureNamesHash;
  private final int numFeatures;

  private String[] createAndCacheFeatureNames(String phraseTableName, String[] phraseScoreNames) {
    final int numEffectiveFeatures = Math.min(this.numFeatures, phraseScoreNames.length);
    String[] featureNames = new String[numEffectiveFeatures];
    for (int i = 0; i < featureNames.length; i++) {
      if (phraseScoreNames[i] != null) {
        featureNames[i] = String.format("%s:%s", PREFIX, phraseScoreNames[i]);
      }
    }
    featureNamesHash.put(phraseTableName, featureNames);
    return featureNames;
  }

  /**
   * Constructor.
   * 
   * @param numFeatures Set to <code>Integer.MAX_VALUE</code> to specify
   * all features defined in the phrase table.
   */
  public PhraseTableScoresFeaturizer(int numFeatures) {
    this.featureNamesHash = Generics.newHashMap();
    this.numFeatures = numFeatures;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<T, String> featurizable) {
    if (featurizable.phraseTableName.equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAME)) {
      // Don't score synthetic rules from hte OOV model
      return null;
    }

    // lookup/construct the list of feature names
    String phraseTableName = featurizable.phraseTableName;
    assert featurizable.phraseScoreNames.length == featurizable.translationScores.length :
      "Score name/value arrays of different dimensions for table: " + phraseTableName;
    String[] featureNames = featureNamesHash.containsKey(phraseTableName) ? 
        featureNamesHash.get(phraseTableName) : 
          createAndCacheFeatureNames(phraseTableName, featurizable.phraseScoreNames);

    // construct array of FeatureValue objects
    List<FeatureValue<String>> features = Generics.newLinkedList();
    for (int i = 0; i < featureNames.length; i++) {
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
