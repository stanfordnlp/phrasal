package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

public class PhrasePairAlignmentConstellation implements
    IsolatedPhraseFeaturizer<IString, String> {

  public final String FEATURE_PREFIX = "ACst:";

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return new FeatureValue<String>(FEATURE_PREFIX
        + f.option.abstractOption.alignment, 1.0);
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
