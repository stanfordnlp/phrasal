package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignments implements RuleFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "ALN";
  private static final String FEATURE_NAME_TGT = "ALNT";
  private static final String FEATURE_NAME_SRC = "ALNS";
  
  private final boolean addSourceDeletions;
  private final boolean addTargetInsertions;
  private final boolean useClasses;
  
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  /**
   * Constructor.
   * 
   */
  public DiscriminativeAlignments() { 
    this.addSourceDeletions = false;
    this.addTargetInsertions = false;
    this.useClasses = false;
  }

  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public DiscriminativeAlignments(String...args) {
    this.addSourceDeletions = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    this.addTargetInsertions = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    this.useClasses = args.length > 2 ? Boolean.parseBoolean(args[2]) : false;
    if (useClasses) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();
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
        List<String> alignedTargetWords = Generics.newArrayList(tgtLength);
        List<String> alignedSourceWords = Generics.newArrayList(srcLength);
        alignedSourceWords.add(sourceRepresentation(srcWord));
        for (int tgtIndex : alignments) {
          alignedTargetWords.add(targetRepresentation(f.targetPhrase.get(tgtIndex)));
          if (hasMultipleAlignments.get(tgtIndex)) {
            int[] srcIndices = alignment.t2s(tgtIndex);
            for (int sIndex : srcIndices) {
              IString srcToken = f.sourcePhrase.get(sIndex);
              alignedSourceWords.add(sourceRepresentation(srcToken));
            }
          }
        }
        
        // Construct the feature string
        StringBuilder sb = new StringBuilder();
        Collections.sort(alignedSourceWords);
        for (String token : alignedSourceWords) {
          if (sb.length() > 0) sb.append("-");
          sb.append(token);
        }
        sb.append(">");
        Collections.sort(alignedTargetWords);
        boolean seenFirst = false;
        for (String token : alignedTargetWords) {
          if (seenFirst) sb.append("-");
          sb.append(token);
          seenFirst = true;
        }
        features.add(new FeatureValue<String>(FEATURE_NAME + ":" + sb.toString(), 1.0));
      }
    }
    return features;
  }
  
  private String sourceRepresentation(IString token) {
    return useClasses ? sourceMap.get(token).toString() : token.toString();
  }
  
  private String targetRepresentation(IString token) {
    return useClasses ? targetMap.get(token).toString() : token.toString();
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
