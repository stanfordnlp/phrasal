package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

public class PhrasePairAlignmentConstellation implements
    RuleFeaturizer<IString, String> {

  public final String FEATURE_PREFIX = "ACst:";

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_PREFIX
        + f.rule.abstractRule.alignment, 1.0));
    return features;
  }

  @Override
  public void initialize() {
  }
}
