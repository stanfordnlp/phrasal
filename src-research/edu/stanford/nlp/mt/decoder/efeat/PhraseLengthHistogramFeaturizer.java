package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class PhraseLengthHistogramFeaturizer implements
    IncrementalFeaturizer<IString, String>,
    IsolatedPhraseFeaturizer<IString, String> {
  public static final String FEATURE_NAME = "PhrLen";

  enum FeatureConfig {
    source, target, sourceAndTarget
  }

  public static final FeatureConfig DEFAULT_CONFIG = FeatureConfig.sourceAndTarget;

  private final FeatureConfig config;

  public PhraseLengthHistogramFeaturizer() {
    config = DEFAULT_CONFIG;
  }

  public PhraseLengthHistogramFeaturizer(String... args) {
    try {
      this.config = FeatureConfig.valueOf(args[0]);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(String.format(
          "Unsupported configuration: %s\n", args[0]));
    }
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    StringBuilder sbuf = new StringBuilder();
    sbuf.append(FEATURE_NAME).append(":");

    switch (config) {
    case source:
      sbuf.append("s:").append(f.foreignPhrase.size());
      break;
    case target:
      sbuf.append("t:").append(f.translatedPhrase.size());
      break;
    case sourceAndTarget:
      sbuf.append("st:").append(f.foreignPhrase.size()).append(">")
          .append(f.translatedPhrase.size());
      break;
    }
    return new FeatureValue<String>(sbuf.toString(), 1.0);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  public void reset() {
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return featurize(f);
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    return listFeaturize(f);
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
