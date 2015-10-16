package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;

/**
 * Indicates the source of this translation rule.
 * 
 * @author Spence Green
 *
 */
public class RuleProvenanceFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "PRV";
  private final boolean prefixOnly;

  /**
   * Reflection constructor.
   * 
   * @param args
   */
  public RuleProvenanceFeaturizer(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.prefixOnly = options.containsKey("prefixOnly");  
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    if ((prefixOnly && f.sourceInputProperties.containsKey(InputProperty.TargetPrefix)) || ! prefixOnly) {
      return f.phraseTableName.equals(Phrasal.TM_BACKGROUND_NAME) ? null :
        Collections.singletonList(new FeatureValue<>(
            FEATURE_NAME + ":" + f.phraseTableName, 1.0));
    }
    return null;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
