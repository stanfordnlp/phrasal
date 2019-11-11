package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.*;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
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
public class RuleUnalignedFeaturizer implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_PREFIX = "UAL";
  private static final String SRC_FEAT = FEATURE_PREFIX + ":src";
  private static final String TGT_FEAT = FEATURE_PREFIX + ":tgt";
  
  private final boolean sourceDel;
  private final boolean targetIns;
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public RuleUnalignedFeaturizer(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.sourceDel = options.containsKey("sourceDel");
    this.targetIns = options.containsKey("targetIns");
  }
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    PhraseAlignment a = f.rule.abstractRule.alignment;
    int numTargetInsertions = 0;
    BitSet sourceAligned = new BitSet(f.sourcePhrase.size());
    for (int i = 0, sz = f.targetPhrase.size(); i < sz; ++i) {
      Set<Integer> t2s = a.t2s(i);
      if (t2s == null || t2s.size() == 0) {
        ++numTargetInsertions;
      } else {
        for (int j : t2s) sourceAligned.set(j);
      }
    }
    List<FeatureValue<String>> features = new ArrayList<>(2);
    if (sourceDel) features.add(new FeatureValue<>(SRC_FEAT, f.sourcePhrase.size() - sourceAligned.cardinality()));
    if (targetIns) features.add(new FeatureValue<>(TGT_FEAT, numTargetInsertions));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
