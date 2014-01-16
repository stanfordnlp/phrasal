package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * A measure of how much punctuation should be translated.
 * 
 * @author Spence Green
 *
 */
public class PunctuationDifference extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString,String> {

  private static final String FEATURE_NAME = "PDIF";
  
  private int numSourcePunctuationTokens;
  
  private final boolean addDomainFeatures;
  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  
  public PunctuationDifference() {
    this.addDomainFeatures = false;
  }
  
  public PunctuationDifference(String...args) {
    this.addDomainFeatures = args.length > 0;
    this.sourceIdInfoMap = addDomainFeatures ? 
        Collections.synchronizedMap(SparseFeatureUtils.loadGenreFile(args[0])) : null;
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    numSourcePunctuationTokens = 0;
    for (IString token : source) {
      if (TokenUtils.isPunctuation(token.toString())) {
        ++numSourcePunctuationTokens;
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (numSourcePunctuationTokens == 0) return null;
    int numTargetPunctuationTokens = 0;
    for (IString token : f.targetPhrase) {
      if (TokenUtils.isPunctuation(token.toString())) {
        ++numTargetPunctuationTokens;
      }
    }
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) ? 
        sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();

    List<FeatureValue<String>> features = Generics.newLinkedList();
    double featureValue = (double) numTargetPunctuationTokens / (double) numSourcePunctuationTokens;
    features.add(new FeatureValue<String>(FEATURE_NAME, featureValue));
    if (genre != null) {
      features.add(new FeatureValue<String>(FEATURE_NAME + "-" + genre, featureValue));
    }
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
