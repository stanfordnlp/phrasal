package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Rule punctuation features.
 * 
 * @author Spence Green
 *
 */
public class RulePunctuation implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_PREFIX = "RPN";

  private final boolean shape;

  /**
   * Constructor.
   * 
   * @param args
   */
  public RulePunctuation(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.shape = options.containsKey("shape");
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    int numSourcePunc = 0;
    for (IString token : f.sourcePhrase)
      if (TokenUtils.isPunctuation(token.toString())) ++numSourcePunc;
    int numTargetPunc = 0;
    for (IString token : f.targetPhrase)
      if (TokenUtils.isPunctuation(token.toString())) ++numTargetPunc;
    final int puncDiff = Math.abs(numTargetPunc - numSourcePunc); 
    List<FeatureValue<String>> features = new ArrayList<>(2);
    if (puncDiff > 0) features.add(new FeatureValue<>(FEATURE_PREFIX, Math.log(puncDiff)));
    if (shape && (numSourcePunc > 0 || numTargetPunc > 0)) features.add(
        new FeatureValue<>(FEATURE_PREFIX + ":" + numSourcePunc + "-" + numTargetPunc, 1.0));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
