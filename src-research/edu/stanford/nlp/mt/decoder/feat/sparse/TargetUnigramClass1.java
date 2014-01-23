package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

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
public class TargetUnigramClass1 implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCLS";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  private final int DEBUG_OPT = 0; // Thang Jan14: >0 print debugging message
  
  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (DEBUG_OPT>0){
      System.err.print("# target unigram\n");
    }
    for (IString token : f.targetPhrase) {
      // Thang Jan14: add individual class features
      List<IString> tokenClasses = targetMap.getList(token);
      if (DEBUG_OPT>0){
        System.err.println(token + " " + tokenClasses);
      }
      
      for (int i = 0; i < tokenClasses.size(); i++) {
        features.add(new FeatureValue<String>(String.format("%s%d:%s",FEATURE_NAME,i,tokenClasses.get(i).toString()), 1.0));
        if (DEBUG_OPT>0){
          System.err.println(String.format("%s%d:%s",FEATURE_NAME,i,tokenClasses.get(i).toString()));
        }
      }
    }
    
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
