package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;
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
  private final boolean addDomainFeatures;
  
  /**
   * Constructor.
   */
  public TargetUnigramClass() {
    this.addDomainFeatures = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetUnigramClass(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFeature");
  }
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    final String genre = addDomainFeatures && f.sourceInputProperties.containsKey(InputProperty.Domain)
        ? (String) f.sourceInputProperties.get(InputProperty.Domain) : null;
    for (IString token : f.targetPhrase) {
      String tokenClass = targetMap.get(token).toString();
      String featureString = String.format("%s:%s",FEATURE_NAME,tokenClass);
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
    }
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
