package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.util.Generics;

/**
 * A measure of how much punctuation should be translated.
 * 
 * @author Spence Green
 *
 */
public class PunctuationDifference extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString,String> {

  private static final String FEATURE_NAME = "PDIF";
  
  private int numSourcePunctuationTokens;
  
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
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, (double) numTargetPunctuationTokens / (double) numSourcePunctuationTokens));
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
