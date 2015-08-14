package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;

/**
 * Indicates the source of this translation rule.
 * 
 * @author Spence Green
 *
 */
public class RuleProvenanceFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "PRV";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    if (f.phraseTableName.equals(Phrasal.TM_FOREGROUND_NAME)) {
      return Collections.singletonList(new FeatureValue<>(
          String.format("%s:%s", FEATURE_NAME, f.phraseTableName), 1.0));
    }
    return null;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
