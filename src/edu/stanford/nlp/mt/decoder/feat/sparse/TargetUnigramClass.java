package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Map;

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
public class TargetUnigramClass implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCLS";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  private final boolean addDomainFeatures;
  private Map<Integer, Pair<String, Integer>> sourceIdInfoMap;
  
  public TargetUnigramClass() {
    this.addDomainFeatures = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetUnigramClass(String...args) {
    addDomainFeatures = args.length > 0;
    sourceIdInfoMap = addDomainFeatures ? SparseFeatureUtils.loadGenreFile(args[0]) : null;
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
