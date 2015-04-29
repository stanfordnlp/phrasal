package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

  // Only construct the feature strings once for each phrase table
  private final ConcurrentHashMap<String, String[]> featureNamesHash;
  private final int numFeatures;

  /**
   * Convert the feature name to a format that is easily greppable in the weight vector.
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
  private String[] createAndCacheFeatureNames(String phraseTableName, String[] phraseScoreNames) {
    String[] featureNames = Arrays.stream(phraseScoreNames).filter(s -> s != null)
        .map(s -> toTMFeature(s)).toArray(String[]::new);
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
    final int numEffectiveFeatures = Math.min(this.numFeatures, featureNames.length);
    assert numEffectiveFeatures <= featurizable.translationScores.length;
    return IntStream.range(0, numEffectiveFeatures).mapToObj(i -> new FeatureValue<String>(
        featureNames[i], featurizable.translationScores[i], true)).collect(Collectors.toList());
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
