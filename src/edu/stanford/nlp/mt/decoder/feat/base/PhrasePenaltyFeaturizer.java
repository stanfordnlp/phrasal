package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Moses phrase penalty generated here so that you don't have to read it
 * countless times from the phrase table.
 * 
 * @author Michel Galley
 * 
 * @param <TK>
 */
public class PhrasePenaltyFeaturizer<TK> implements
    RuleFeaturizer<TK, String> {
  static public String FEATURE_NAME = "TM:phrasePenalty";
  // mg2008: please don't change to "= 1" since not exactly the same value:
  private double phrasePenalty = Math.log(2.718);

  public PhrasePenaltyFeaturizer(String... args) {
    if (args.length >= 1) {
      assert (args.length == 1);
      phrasePenalty = Double.parseDouble(args[0]);
    }
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<TK, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(FEATURE_NAME, phrasePenalty));
    return features;
  }

  @Override
  public void initialize() {
  }

  @Override
  public boolean constructInternalAlignments() {
    return false;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
