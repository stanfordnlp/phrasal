package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Target class unigram insertion.
 * 
 * @author Spence Green
 *
 */
public class TargetUnigramClass implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCLS";
  
  @Override
  public void initialize() {
    if (! TargetClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
    }
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    for (IString token : f.targetPhrase) {
      IString tokenClass = TargetClassMap.get(token);
      features.add(new FeatureValue<String>(String.format("%s:%s",FEATURE_NAME,tokenClass.toString()), 1.0));
    }
    return features;
  }
}
