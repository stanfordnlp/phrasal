package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class LinearDistortionFeaturizer<TK> implements
    DerivationFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "DebugLinearDistortionFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static final String FEATURE_NAME = "LinearDistortion";

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<TK, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, -1.0 * f.linearDistortion));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<TK,String>> options, Sequence<TK> foreign) {
  }
}
