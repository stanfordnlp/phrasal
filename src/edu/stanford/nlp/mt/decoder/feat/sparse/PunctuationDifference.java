package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;


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
      Sequence<IString> source) {
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
    List<FeatureValue<String>> features = new LinkedList<>();
    double featureValue = (double) numTargetPunctuationTokens / (double) numSourcePunctuationTokens;
    features.add(new FeatureValue<String>(FEATURE_NAME, featureValue));
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
