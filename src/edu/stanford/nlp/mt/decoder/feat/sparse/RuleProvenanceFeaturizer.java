package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

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
  public static final String SOURCE_WORDS = "srcWrd";  
  public static final String TARGET_WORDS = "tgtWrd";

  private final boolean wordFeatures;
  
  public RuleProvenanceFeaturizer(String...args) {
    Properties opts = FeatureUtils.argsToProperties(args);
    wordFeatures = opts.containsKey("wordFeatures");
  }
  
  public RuleProvenanceFeaturizer() {
    wordFeatures = false;
  }
  
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    if(f.phraseTableName.equals(Phrasal.TM_BACKGROUND_NAME)) return null;
    
    List<FeatureValue<String>> features = new ArrayList<>();
    features.add(new FeatureValue<>(FEATURE_NAME + ":" + f.phraseTableName, 1.0));
    
    if(wordFeatures) {
      features.add(new FeatureValue<>(FEATURE_NAME + ":" + SOURCE_WORDS + ":" + f.phraseTableName, f.sourcePhrase.size()));
      features.add(new FeatureValue<>(FEATURE_NAME + ":" + TARGET_WORDS + ":" + f.phraseTableName, f.targetPhrase.size()));
    }
    
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
