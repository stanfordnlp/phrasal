package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;


/**
 * Add a penalty for the number of unaligned source and target words.
 * 
 * @author Spence Green
 *
 */
public class AlignmentPenalty implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "ACNT";
  
  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    boolean[] sourceCoverage = new boolean[f.sourcePhrase.size()];
    
    int nUnalignedTarget = 0;
    for (int i = 0, max = f.targetPhrase.size(); i < max; ++i) {
      int[] alignments = alignment.t2s(i);
      if (alignments == null) {
        // Unaligned target token
        ++nUnalignedTarget;
      } else {
        for (int j : alignments) {
          sourceCoverage[j] = true;
        }
      }
    }
    int nUnalignedSource = 0;
    for (boolean covered : sourceCoverage) {
      // Unaligned source token
      if (! covered) ++nUnalignedSource;
    }
    
    List<FeatureValue<String>> features = new LinkedList<>();
    features.add(new FeatureValue<String>(FEATURE_PREFIX + "src", nUnalignedSource));
    features.add(new FeatureValue<String>(FEATURE_PREFIX + "tgt", nUnalignedTarget));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }

}
