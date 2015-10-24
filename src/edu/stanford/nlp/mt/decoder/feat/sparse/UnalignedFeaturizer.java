package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;

/**
 * Penalize source deletions and target insertions.
 * 
 * @author Spence Green
 *
 */
public class UnalignedFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_PREFIX = "UAL";
  private static final String SRC_FEAT = FEATURE_PREFIX + ":src";
  private static final String TGT_FEAT = FEATURE_PREFIX + ":tgt";
  
  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    PhraseAlignment a = f.rule.abstractRule.alignment;
    int numTargetInsertions = 0;
    BitSet sourceAligned = new BitSet(f.sourcePhrase.size());
    for (int i = 0, sz = f.targetPhrase.size(); i < sz; ++i) {
      int[] t2s = a.t2s(i);
      if (t2s == null || t2s.length == 0) {
        ++numTargetInsertions;
      } else {
        for (int j : t2s) sourceAligned.set(j);
      }
    }
    List<FeatureValue<String>> features = new ArrayList<>(2);
    features.add(new FeatureValue<>(SRC_FEAT, f.sourcePhrase.size() - sourceAligned.cardinality()));
    features.add(new FeatureValue<>(TGT_FEAT, numTargetInsertions));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
