package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
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
  
  private final boolean addDomainFeatures;
  
  /**
   * Constructor.
   */
  public PunctuationDifference() {
    this.addDomainFeatures = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public PunctuationDifference(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFeature");
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
    final String genre = addDomainFeatures && f.sourceInputProperties.containsKey(InputProperty.Domain)
        ? (String) f.sourceInputProperties.get(InputProperty.Domain) : null;

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
