package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;


/**
 * Shape of the translation rule.
 * 
 * @author Spence Green
 *
 */
public class RuleShape implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "RSHP";
  private final boolean prefixOnly;

  /**
   * Reflection constructor.
   * 
   * @param args
   */
  public RuleShape(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.prefixOnly = options.containsKey("prefixOnly");  
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    if ((prefixOnly && f.sourceInputProperties.containsKey(InputProperty.TargetPrefix)) || ! prefixOnly) {
      return Collections.singletonList(new FeatureValue<>(
          FEATURE_NAME + ":" + f.sourcePhrase.size() + "-" + f.targetPhrase.size(), 1.0));
    }
    return null;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
