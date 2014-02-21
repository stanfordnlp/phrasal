package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Target class unigram insertion.
 * 
 * @author Spence Green
 *
 */
public class TargetUnigramClass1 implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCLS";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  private final boolean addDomainFeatures;
  private Map<Integer, Pair<String, Integer>> sourceIdInfoMap;
  
  private final int DEBUG_OPT = 1; // Thang Jan14: >0 print debugging message
  
  public TargetUnigramClass1() {
    this.addDomainFeatures = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetUnigramClass1(String...args) {
    Properties options = SparseFeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFile");
//    if (addDomainFeatures) {
//      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
//    }
  }
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) ? 
        sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();
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
        String featureString = String.format("%s%d:%s",FEATURE_NAME,i,tokenClasses.get(i).toString());
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (DEBUG_OPT>0){
          System.err.println(featureString);
        }
        if (genre != null) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
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
