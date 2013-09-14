package edu.stanford.nlp.mt.decoder.efeat;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.NeedsInternalAlignments;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignments implements NeedsInternalAlignments,
RuleFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "ALN";
  private static final String FEATURE_NAME_TGT = "ALNT";
  private static final String FEATURE_NAME_SRC = "ALNS";

  private final boolean addSourceDeletions;
  private final boolean addTargetInsertions;
  private final boolean sourceClasses;
  private final boolean targetClasses;
  
  /**
   * Constructor.
   * 
   */
  public DiscriminativeAlignments() { 
    this.addSourceDeletions = false;
    this.addTargetInsertions = false;
    this.sourceClasses = false;
    this.targetClasses = false;
  }

  /**
   * Constructor.
   * 
   * @param args
   */
  public DiscriminativeAlignments(String...args) {
    this.addSourceDeletions = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    this.addTargetInsertions = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    this.sourceClasses = args.length > 2 ? Boolean.parseBoolean(args[2]) : false;
    this.targetClasses = args.length > 3 ? Boolean.parseBoolean(args[3]) : false;
    
    if (sourceClasses && ! SourceClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.SOURCE_CLASS_MAP + " decoder option");
    }
    if (targetClasses && ! TargetClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
    }
  }

  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    final int tgtLength = f.targetPhrase.size();
    final int srcLength = f.sourcePhrase.size();
    List<Set<Integer>> s2t = Generics.newArrayList(srcLength);
    for (int i = 0; i < srcLength; ++i) {
      s2t.add(new HashSet<Integer>());
    }
    BitSet hasMultipleAlignments = new BitSet(tgtLength);
    List<FeatureValue<String>> features = Generics.newLinkedList();

    // Target-side alignments
    for (int i = 0; i < tgtLength; ++i) {
      int[] alignments = alignment.t2s(i);
      if (alignments == null) {
        if (addTargetInsertions) {
          IString tgtWord = f.targetPhrase.get(i);
          features.add(new FeatureValue<String>(
              FEATURE_NAME_TGT + ":" + targetRepresentation(tgtWord), 1.0));
        }

      } else {
        if (alignments.length > 1) {
          hasMultipleAlignments.set(i);
        }
        for (int j : alignments) {
          s2t.get(j).add(i);
        }
      }
    }

    // Source-side alignments
    for (int i = 0; i < srcLength; ++i) {
      Set<Integer> alignments = s2t.get(i);
      IString srcWord = f.sourcePhrase.get(i);
      if (alignments.size() == 0) {
        if (addSourceDeletions) {
          features.add(new FeatureValue<String>(
              FEATURE_NAME_SRC + ":" + sourceRepresentation(srcWord), 1.0));
        }
        
      } else {
        List<String> alignedWords = Generics.newArrayList(srcLength+tgtLength);
        alignedWords.add(sourceRepresentation(srcWord));
        for (int tgtIndex : alignments) {
          alignedWords.add(targetRepresentation(f.targetPhrase.get(tgtIndex)));
          if (hasMultipleAlignments.get(tgtIndex)) {
            int[] srcIndices = alignment.t2s(tgtIndex);
            for (int sIndex : srcIndices) {
              IString srcToken = f.sourcePhrase.get(sIndex);
              alignedWords.add(sourceRepresentation(srcToken));
            }
          }
        }
        StringBuilder sb = new StringBuilder();
        Collections.sort(alignedWords);
        for (String token : alignedWords) {
          if (sb.length() > 0) sb.append("-");
          sb.append(token);
        }
        features.add(new FeatureValue<String>(FEATURE_NAME + ":" + sb.toString(), 1.0));
      }
    }
    return features;
  }
  
  private String sourceRepresentation(IString token) {
    return sourceClasses ? SourceClassMap.get(token).toString() : token.toString();
  }
  
  private String targetRepresentation(IString token) {
    return targetClasses ? TargetClassMap.get(token).toString() : token.toString();
  }
}
