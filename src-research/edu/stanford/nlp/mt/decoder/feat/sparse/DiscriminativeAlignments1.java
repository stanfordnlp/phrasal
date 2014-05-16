package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignments1 implements RuleFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "ALN";
  private static final String FEATURE_NAME_TGT = "ALNT";
  private static final String FEATURE_NAME_SRC = "ALNS";
  
  private final boolean addSourceDeletions;
  private final boolean addTargetInsertions;
  private final boolean useClasses;
  private final boolean addDomainFeatures;
  
  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  private final int DEBUG_OPT = 1; // Thang Jan14: >0 print debugging message
  
  /**
   * Constructor.
   * 
   */
  public DiscriminativeAlignments1() { 
    this.addSourceDeletions = false;
    this.addTargetInsertions = false;
    this.useClasses = false;
    this.addDomainFeatures = false;
  }

  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public DiscriminativeAlignments1(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addSourceDeletions = options.containsKey("sourceDeletionFeature");
    this.addTargetInsertions = options.containsKey("targetInsertionFeature");
    this.useClasses = options.containsKey("useClasses");
    if (useClasses) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();
    }
    this.addDomainFeatures = options.containsKey("domainFile");
//    if (addDomainFeatures) {
//      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
//    }
  }

  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) 
        ? sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();

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
          
          // Thang Jan14: add individual class features
          List<String> representations = targetRepresentation(tgtWord); 
          if (DEBUG_OPT>0){
            System.err.println("# target insertion " + tgtWord + " : " + representations);
          }
          for (int j = 0; j < representations.size(); j++) {
            String featureString = FEATURE_NAME_TGT + j + ":" + representations.get(j);
            features.add(new FeatureValue<String>(featureString, 1.0));
            
            if (DEBUG_OPT>0){
              System.err.println("  " + featureString);
            }
            if (genre != null) {
              features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
            }
          }
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
          // Thang Jan14: add individual class features
          List<String> representations = sourceRepresentation(srcWord); 
          if (DEBUG_OPT>0){
            System.err.println("# source deletion " + srcWord + " : " + representations);
          }
          for (int j = 0; j < representations.size(); j++) {
            String featureString = FEATURE_NAME_SRC + j + ":" + representations.get(j);
            features.add(new FeatureValue<String>(featureString, 1.0));
            
            if (DEBUG_OPT>0){
              System.err.println("  " + featureString);
            }
            if (genre != null) {
              features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
            }
          }
        }

        
      } else {
        // Thang Jan14: use IString instead of String to look up classes later
        // Use sets so that multiple alignments aren't counted twice
        Set<IString> alignedTargetWords = new TreeSet<IString>();
        Set<IString> alignedSourceWords = new TreeSet<IString>();
        
        alignedSourceWords.add(srcWord);
        for (int tgtIndex : alignments) {
          alignedTargetWords.add(f.targetPhrase.get(tgtIndex));
          
          if (hasMultipleAlignments.get(tgtIndex)) {
            int[] srcIndices = alignment.t2s(tgtIndex);
            
            for (int sIndex : srcIndices) {
              IString srcToken = f.sourcePhrase.get(sIndex);
              alignedSourceWords.add(srcToken);
            }
          }
        }
        
        /* Thang Jan14: build numClasses StringBuilder in parallel */
        int numClasses = (useClasses) ? sourceMap.getNumMappings() : 1;
        List<StringBuilder> sbs = new ArrayList<StringBuilder>();
        for (int j = 0; j < numClasses; j++) {
          sbs.add(new StringBuilder());
        }
        
        // src
        if (DEBUG_OPT>0){
          System.err.print("# alignment\n  src:");
        }
        for (IString token : alignedSourceWords) {
          if (DEBUG_OPT>0){
            System.err.print(" " + token.toString());
          }
          List<String> tokens = sourceRepresentation(token);
          for (int j = 0; j < numClasses; j++) {
            StringBuilder sb = sbs.get(j);
            if (sb.length() > 0) sb.append("-");
            sb.append(tokens.get(j));
          }
        }
        if (DEBUG_OPT>0){
          System.err.print("\n  tgt:");
        }
        // delimiter
        for (int j = 0; j < numClasses; j++) {
          sbs.get(j).append(">");
        }
        // tgt
        boolean seenFirst = false;
        for (IString token : alignedTargetWords) {
          if (DEBUG_OPT>0){
            System.err.print(" " + token.toString());
          }
          List<String> tokens = targetRepresentation(token);
          for (int j = 0; j < numClasses; j++) {
            StringBuilder sb = sbs.get(j);
            
            if (seenFirst) sb.append("-");
            sb.append(tokens.get(j));
          }
          
          seenFirst = true;
        }
        if (DEBUG_OPT>0){
          System.err.println();
        }
        
        // feature strings
        for (int j = 0; j < numClasses; j++) {
          String featureString = FEATURE_NAME + j + ":" + sbs.get(j).toString();
          features.add(new FeatureValue<String>(featureString, 1.0));
          if (DEBUG_OPT>0){
            System.err.println("  " + featureString);
          }
          if (genre != null) {
            features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
          }  
        }
      }
    }
    return features;
  }
  
  // Thang Jan14: return a list of representations instead
  private List<String> sourceRepresentation(IString token) {
    List<String> representations = new ArrayList<String>();
    if(useClasses){
      for(IString representation : sourceMap.getList(token)){
        representations.add(representation.toString());
      }
    } else {
      representations.add(token.toString());
    }
    return representations;
  }
  
  // Thang Jan14: return a list of representations instead
  private List<String> targetRepresentation(IString token) {
    List<String> representations = new ArrayList<String>();
    if(useClasses){
      for(IString representation : targetMap.getList(token)){
        representations.add(representation.toString());
      }
    } else {
      representations.add(token.toString());
    }
    return representations;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
