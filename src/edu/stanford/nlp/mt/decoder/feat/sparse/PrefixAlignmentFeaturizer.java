package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Prefix alignment features.
 * 
 * @author Spence Green
 *
 */
public class PrefixAlignmentFeaturizer extends DerivationFeaturizer<IString, String> 
implements RuleFeaturizer<IString,String>{

  public static final String FEATURE_PREFIX = "PRF";
  
  // Rule features
  private final boolean rUnaligned;
  private final boolean rShape;
  
  // Derivation features
  private final boolean dDiagDistance;
  private final boolean dOrthoSim;
  public PrefixAlignmentFeaturizer(String...args) {
    Properties opts = FeatureUtils.argsToProperties(args);
    dDiagDistance = opts.containsKey("dDiagDistance");
    dOrthoSim = opts.containsKey("dOrthoSim");
    
    // Rule features
    rUnaligned = opts.containsKey("rUnaligned");
    rShape = opts.containsKey("rShape");
  }
  
  @Override
  public void initialize(int sourceInputId, Sequence<IString> source) {
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    //if (f.targetPosition >= f.derivation.prefixLength || f.targetPhrase.size() == 0) return null;
    List<FeatureValue<String>> features = new ArrayList<>();
    if (dDiagDistance) features.addAll(diagonalDistance(f));
    
    return features;
  }

  private static final String EXACT_MATCH = FEATURE_PREFIX + ":ex";
  private List<FeatureValue<String>> orthoSimilarity(Featurizable<IString, String> f) {
    Sequence<IString> source = f.sourcePhrase;
    Sequence<IString> target = f.targetPhrase;
    int srcSize = source.size();
    int tgtSize = target.size();
    List<FeatureValue<String>> features = new ArrayList<>();
    // Exact match
    if (srcSize == 1 && tgtSize == 1 && source.get(0).id == target.get(0).id) {
      // Optimization for unigrams
      features.add(new FeatureValue<>(EXACT_MATCH, 1.0));
    } else if (source.equals(target)) {
      features.add(new FeatureValue<>(EXACT_MATCH, 1.0));
    }
    return features;
  }

  // "Distance" features from Taskar et al. (2005)
  private static final String DIAG_DISTANCE = FEATURE_PREFIX + ":diag";
  private static final String DIAG_DISTANCE_SQ = FEATURE_PREFIX + ":diagsq";
  private static final String DIAG_DISTANCE_SQRT = FEATURE_PREFIX + ":diagsqrt";
  private List<FeatureValue<String>> diagonalDistance(Featurizable<IString, String> f) {
    final int distortion = Math.abs(f.sourcePosition - f.targetPosition);
    List<FeatureValue<String>> features = new ArrayList<>(3);
    features.add(new FeatureValue<>(DIAG_DISTANCE, distortion));
    features.add(new FeatureValue<>(DIAG_DISTANCE_SQ, distortion*distortion));
    features.add(new FeatureValue<>(DIAG_DISTANCE_SQRT, Math.sqrt(distortion)));
    return features;
  }

  /**
   * Rule featurizer.
   */
  
  @Override
  public void initialize() {}

  private static final String RULE_SHAPE = FEATURE_PREFIX + ":shp";
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    //if ( ! f.sourceInputProperties.containsKey(InputProperty.TargetPrefix)) return null;
    List<FeatureValue<String>> features = new ArrayList<>();
    if (rUnaligned) features.addAll(unalignedWords(f));
    if (rShape) {
      String srcLen = Integer.toString(f.sourcePhrase.size());
      String tgtLen = Integer.toString(f.targetPhrase.size());
      features.add(new FeatureValue<>(RULE_SHAPE + ":" + srcLen + "-" + tgtLen, 1.0));
    }
    if (dOrthoSim) features.addAll(orthoSimilarity(f));
    
    return features;
  }

  private static final String UAL_SRC = FEATURE_PREFIX + ":uals";
  private static final String UAL_TGT = FEATURE_PREFIX + ":ualt";
  private List<FeatureValue<String>> unalignedWords(Featurizable<IString, String> f) {
    PhraseAlignment a = f.rule.abstractRule.alignment;
    int numTargetInsertions = 0;
    BitSet sourceAligned = new BitSet(f.sourcePhrase.size());
    for (int i = 0, sz = f.targetPhrase.size(); i < sz; ++i) {
      int[] t2s = a.t2s(i);
      if (t2s == null) {
        ++numTargetInsertions;
      } else {
        for (int j : t2s) sourceAligned.set(j);
      }
    }
    List<FeatureValue<String>> features = new ArrayList<>(2);
    features.add(new FeatureValue<>(UAL_SRC, f.sourcePhrase.size() - sourceAligned.cardinality()));
    features.add(new FeatureValue<>(UAL_TGT, numTargetInsertions));
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }  
}
