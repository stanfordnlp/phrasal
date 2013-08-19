package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsInternalAlignments;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.process.DistSimClassifier;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * Uses distributional similarity clusters as a feature for a given zh/en phrase pair,
 * both entire sequences and internally aligned words in the pair
 * 
 * @author robvoigt
 * 
 */
public class DistSimFeaturizer implements
    RuleFeaturizer<IString, String>, NeedsInternalAlignments {

  DistSimClassifier enDistSim;
  DistSimClassifier zhDistSim;
  public static final String EN_DISTSIM_FILE = "/u/nlp/data/pos_tags_are_useless/egw4-reut.512.clusters";
  public static final String ZH_DISTSIM_FILE = "/u/nlp/data/chinese/distsim/xin_cmn_2000-2010.ldc.seg.utf8.1M.random-c1000";
  public static final String FEATURE_NAME = "DistSim";

  @Override
  public void initialize() {
    enDistSim = new DistSimClassifier(EN_DISTSIM_FILE, false, true);
    zhDistSim = new DistSimClassifier(ZH_DISTSIM_FILE, false, true);
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    
    if (f.sourcePhrase == null || f.targetPhrase == null) {
      return null;
    }

    Integer srcLength = f.sourcePhrase.size();
    Integer tgtLength = f.targetPhrase.size();
    
    List<String> srcDistSimSequence = Generics.newLinkedList(); 
    for (int i = 0; i < srcLength; ++i) {
      String srcWord = f.sourcePhrase.get(i).toString();
      srcDistSimSequence.add(zhDistSim.distSimClass(srcWord));
    }
        
    List<String> tgtDistSimSequence = Generics.newLinkedList();
    for (int i = 0; i < tgtLength; ++i) {
      String tgtWord = f.targetPhrase.get(i).toString();
      tgtDistSimSequence.add(enDistSim.distSimClass(tgtWord));
    }
    
    String feature = FEATURE_NAME + "-Src:" + StringUtils.join(srcDistSimSequence,":") + "-Tgt:" + StringUtils.join(tgtDistSimSequence,":"); // relatively sparse feature to compare full sequences    
    features.add(new FeatureValue<String>(feature, 1.0));
    
    for (int tgtIndex = 0; tgtIndex < tgtLength; ++tgtIndex) {
      int[] srcIndices = alignment.t2s(tgtIndex);
      for (int srcIndex : srcIndices) {
        feature = FEATURE_NAME + "-Src:" + srcDistSimSequence.get(srcIndex) + "-Tgt:" + tgtDistSimSequence.get(tgtIndex);
        features.add(new FeatureValue<String>(feature, 1.0));
      }
    }
    return features;
  }
}

