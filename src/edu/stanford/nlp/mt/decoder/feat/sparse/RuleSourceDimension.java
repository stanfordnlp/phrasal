package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Source dimension of the rule.
 * 
 * @author Spence Green
 *
 */
public class RuleSourceDimension implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "SRCD";
  
  private final boolean addDomainFeatures;
  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  
  public RuleSourceDimension() { 
    this.addDomainFeatures = false;
  }
  
  public RuleSourceDimension(String...args) {
    Properties options = SparseFeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFile");
    if (addDomainFeatures) {
      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
    }
  }
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    String featureString = String.format("%s:%d",FEATURE_NAME, f.sourcePhrase.size());
    features.add(new FeatureValue<String>(featureString, 1.0));
    if (addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId)) {
      Pair<String,Integer> genreInfo = sourceIdInfoMap.get(f.sourceInputId);
      String genre = genreInfo.first();
      features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
    }
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
