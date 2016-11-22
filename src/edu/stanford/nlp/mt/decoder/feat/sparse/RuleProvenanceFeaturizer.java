package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Indicates the source of this translation rule.
 * 
 * @author Spence Green
 *
 */
public class RuleProvenanceFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "PRV";
  public static final String SOURCE_WORDS = FEATURE_NAME + ":srcWrd";  
  public static final String TARGET_WORDS = FEATURE_NAME + ":tgtWrd";
  public static final String FULL_SEGMENT = FEATURE_NAME + ":fullSegment";

  public static final String UNK_TM = "edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator";
  
  private final boolean wordFeatures;
  private final boolean fullSegmentFeature; // requires feature set DENSE_EXT_GREEN
  
  private ConcurrentHashMap<String, String> phraseFeatMap = null;
  private ConcurrentHashMap<String, String> srcWordsFeatMap = null;
  private ConcurrentHashMap<String, String> tgtWordsFeatMap = null;
  private ConcurrentHashMap<String, String> fullSegmentFeatMap = null;
  
  public RuleProvenanceFeaturizer(String...args) {
    Properties opts = FeatureUtils.argsToProperties(args);
    wordFeatures = opts.containsKey("wordFeatures");
    fullSegmentFeature = opts.containsKey("fullSegmentFeature");
    initFeatureMaps();
  }
  
  public RuleProvenanceFeaturizer() {
    wordFeatures = false;
    fullSegmentFeature = false;
    initFeatureMaps();
  }
  
  private void initFeatureMaps() {
    phraseFeatMap = new ConcurrentHashMap<>();
    if(wordFeatures) {
      srcWordsFeatMap = new ConcurrentHashMap<>();
      tgtWordsFeatMap = new ConcurrentHashMap<>();
    }
    if(fullSegmentFeature) {
      fullSegmentFeatMap = new ConcurrentHashMap<>();
    }
  }
  
  @Override
  public void initialize() {}

  private String getFeatureName(String phraseTableName, ConcurrentHashMap<String, String> map, String featureLabel) {
    String result = map.get(phraseTableName);
    if(result != null) return result;
    
    result = featureLabel + ":" + phraseTableName;
    map.put(phraseTableName, result);
    return result;
  }
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    if(f.phraseTableName.equals(Phrasal.TM_BACKGROUND_NAME) || f.phraseTableName.equals(UNK_TM)) return null;
    
    List<FeatureValue<String>> features = new ArrayList<>();
    features.add(new FeatureValue<>(getFeatureName(f.phraseTableName, phraseFeatMap, FEATURE_NAME), 1.0));
    
    if(wordFeatures) {
      features.add(new FeatureValue<>(getFeatureName(f.phraseTableName, srcWordsFeatMap, SOURCE_WORDS), f.sourcePhrase.size()));
      features.add(new FeatureValue<>(getFeatureName(f.phraseTableName, tgtWordsFeatMap, TARGET_WORDS), f.targetPhrase.size()));
    }
    
    if(fullSegmentFeature && 
        (f.phraseTableName.equals(Phrasal.TM_FOREGROUND_NAME) || f.phraseTableName.equals(Phrasal.TM_TERMBASE_NAME)) &&
        f.rule.abstractRule.scores.length >= 10 && // i.e. using DENSE_EXT_GREEN feature template
        f.rule.abstractRule.scores[8] != 0.0) { // i.e. phrase is a full segment
      features.add(new FeatureValue<>(getFeatureName(f.phraseTableName, fullSegmentFeatMap, FULL_SEGMENT), 1.0));
    }
    
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
