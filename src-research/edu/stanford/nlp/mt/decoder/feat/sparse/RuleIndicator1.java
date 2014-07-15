package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Indicator features for each rule in a derivation.
 * 
 * @author Daniel Cer
 * @author Spence Green
 * 
 */
public class RuleIndicator1 implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "DPT";

  private static final int DEFAULT_LEXICAL_CUTOFF = 50;
  
  private final boolean addLexicalizedRule;
  private final boolean addClassBasedRule;
  private final int countFeatureIndex;
  private final int lexicalCutoff;
  private final boolean addDomainFeatures;

  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  private final int DEBUG_OPT = 1; // Thang Jan14: >0 print debugging message
  
  /**
   * Constructor.
   */
  public RuleIndicator1() {
    this.addLexicalizedRule = true;
    this.addClassBasedRule = false;
    this.countFeatureIndex = -1;
    this.lexicalCutoff = DEFAULT_LEXICAL_CUTOFF;
    this.addDomainFeatures = false;
  }

  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public RuleIndicator1(String... args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addLexicalizedRule = options.containsKey("addLexicalized");
    this.addClassBasedRule = options.contains("addClassBased");
    this.countFeatureIndex = PropertiesUtils.getInt(options, "countFeatureIndex", -1);
    if (addClassBasedRule) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();
    }
    this.addDomainFeatures = options.containsKey("domainFile");
//    if (addDomainFeatures) {
//      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
//    }
    this.lexicalCutoff = PropertiesUtils.getInt(options, "lexicalCutoff", DEFAULT_LEXICAL_CUTOFF);
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) ? 
        sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();
    
    if (addLexicalizedRule && aboveThreshold(f.rule)) {
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      String featureString = FEATURE_NAME + ":" + String.format("%s>%s", sourcePhrase, targetPhrase);
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
    }
    if (addClassBasedRule) {
      int numClasses = sourceMap.getNumMappings();
      /* Thang Jan14: handle multiple class files */
      List<StringBuilder> sbs = new ArrayList<StringBuilder>(); // build numClasses StringBuilder in parallel
      for (int i = 0; i < numClasses; i++) {
        sbs.add(new StringBuilder());
      }
      if (DEBUG_OPT>0){
        System.err.print("# phrase table\n  src:");
      }
      // src
      for (IString token : f.sourcePhrase) {
        if (DEBUG_OPT>0){
          System.err.print(" " + token.toString());
        }
        
        List<IString> tokens = sourceMap.getList(token);
        for (int i = 0; i < numClasses; i++) {
          StringBuilder sb = sbs.get(i);
          if (sb.length() > 0) sb.append("-");
          sb.append(tokens.get(i).toString());
        }
      }
      if (DEBUG_OPT>0){
        System.err.print("\n  tgt:");
      }
      // delimiter
      for (int i = 0; i < numClasses; i++) {
        sbs.get(i).append(">");
      }
      // tgt
      boolean seenFirst = false;
      for (IString token : f.targetPhrase) {
        if (DEBUG_OPT>0){
          System.err.print(" " + token.toString());
        }
        
        List<IString> tokens = targetMap.getList(token);
        for (int i = 0; i < numClasses; i++) {
          StringBuilder sb = sbs.get(i);
          
          if (seenFirst) sb.append("-");
          sb.append(tokens.get(i).toString());
        }
        
        seenFirst = true;
      }
      if (DEBUG_OPT>0){
        System.err.println();
      }
      
      // feature strings
      for (int i = 0; i < numClasses; i++) {
        String featureString = FEATURE_NAME + i + ":" + sbs.get(i).toString();
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (DEBUG_OPT>0){
          System.err.println("  " + featureString);
        } 
        if (genre != null) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
        }  
      }
    }
    return features;
  }

  private boolean aboveThreshold(ConcreteRule<IString, String> rule) {
    if (countFeatureIndex < 0) return true;
    if (countFeatureIndex >= rule.abstractRule.scores.length) {
      // Generated by unknown word model...don't know count.
      return false;
    }
    int count = (int) Math.round(Math.exp(rule.abstractRule.scores[countFeatureIndex]));
    return count > lexicalCutoff;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
