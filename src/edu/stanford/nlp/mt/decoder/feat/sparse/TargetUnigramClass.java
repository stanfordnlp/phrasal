package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.Generics;

/**
 * Target class unigram insertion.
 * 
 * @author Spence Green
 *
 */
public class TargetUnigramClass implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCLS";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    for (IString token : f.targetPhrase) {
      String tokenClass = targetMap.get(token).toString();
      String featureString = String.format("%s:%s",FEATURE_NAME,tokenClass);
      features.add(new FeatureValue<String>(featureString, 1.0));
    }
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
