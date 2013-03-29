package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.*;
import edu.stanford.nlp.util.Index;

/**
 * @author Michel Galley
 * 
 * @param <TK>
 */
public class StepFunctionDistortionFeaturizer<TK> implements
    IncrementalFeaturizer<TK, String> {

  public static final String DEBUG_PROPERTY = "DebugPollyDistortionFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  private String FEATURE_NAME = "StepFunctionDistortion";
  private double STEP = 6.0;

  public StepFunctionDistortionFeaturizer(String... args) {
    String stepStr = args[0];
    STEP = Double.parseDouble(stepStr);
    FEATURE_NAME += ":" + Double.toString(STEP);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<TK, String> f) {
    assert (f.linearDistortion >= 0);
    return new FeatureValue<String>(FEATURE_NAME,
        f.linearDistortion > STEP ? -1.0 : 0.0);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<TK, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<TK,String>> options,
      Sequence<TK> foreign, Index<String> featureIndex) {
  }

  public void reset() {
  }
}
