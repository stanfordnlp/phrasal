package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;


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
    Properties options = FeatureUtils.argsToProperties(args);
    this.addSourceDeletions = options.containsKey("sourceDeletionFeature");
    this.addTargetInsertions = options.containsKey("targetInsertionFeature");
    this.useClasses = options.containsKey("useClasses");
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
    List<Set<Integer>> s2t = new ArrayList<>(srcLength);
    for (int i = 0; i < srcLength; ++i) {
      s2t.add(new HashSet<Integer>());
    }
    BitSet hasMultipleAlignments = new BitSet(tgtLength);
    List<FeatureValue<String>> features = new LinkedList<>();

    // Target-side alignments
    for (int i = 0; i < tgtLength; ++i) {
      Set<Integer> alignments = alignment.t2s(i);
      if (alignments == null) {
        if (addTargetInsertions) {
          IString tgtWord = f.targetPhrase.get(i);
          String featureString = FEATURE_NAME_TGT + ":" + targetRepresentation(tgtWord);
          features.add(new FeatureValue<String>(featureString, 1.0));
        }

      } else {
        if (alignments.size() > 1) {
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
          String featureString = FEATURE_NAME_SRC + ":" + sourceRepresentation(srcWord);
          features.add(new FeatureValue<String>(featureString, 1.0));
        }
        
      } else {
        // Use sets so that multiple alignments aren't counted twice
        Set<String> alignedTargetWords = new TreeSet<String>();
        Set<String> alignedSourceWords = new TreeSet<String>();
        alignedSourceWords.add(sourceRepresentation(srcWord));
        for (int tgtIndex : alignments) {
          alignedTargetWords.add(targetRepresentation(f.targetPhrase.get(tgtIndex)));
          if (hasMultipleAlignments.get(tgtIndex)) {
            Set<Integer> srcIndices = alignment.t2s(tgtIndex);
            for (int sIndex : srcIndices) {
              IString srcToken = f.sourcePhrase.get(sIndex);
              alignedSourceWords.add(sourceRepresentation(srcToken));
            }
          }
        }
        
        // Construct the feature string
        StringBuilder sb = new StringBuilder();
        for (String token : alignedSourceWords) {
          if (sb.length() > 0) sb.append("-");
          sb.append(token);
        }
        sb.append(">");
        boolean seenFirst = false;
        for (String token : alignedTargetWords) {
          if (seenFirst) sb.append("-");
          sb.append(token);
          seenFirst = true;
        }
        String featureString = FEATURE_NAME + ":" + sb.toString();
        features.add(new FeatureValue<String>(featureString, 1.0));
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
