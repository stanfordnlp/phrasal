package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.Generics;

/**
 * Indicator features for each rule in a derivation.
 * 
 * @author Spence Green
 * 
 */
public class PTMRuleIndicator implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "PTMDPT";

  private final boolean addLexicalizedRule;
  private final boolean addClassBasedRule;
  private final String ptName;
  
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  /**
   * Constructor for reflection loading.
   * 
   * @param args
   */
  public PTMRuleIndicator(String... args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addLexicalizedRule = options.containsKey("addLexicalized");
    this.addClassBasedRule = options.containsKey("addClassBased");
    if (! options.containsKey("ptName")) {
      throw new RuntimeException("No phrase table name specified");
    }
    // See FlatPhraseTable.java
    this.ptName = String.format("FlatPhraseTable(%s)", options.getProperty("ptName"));

    if (addClassBasedRule) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();
    }
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (f.rule.phraseTableName.equals(ptName)) {
      features.add(new FeatureValue<String>(FEATURE_NAME, 1.0));
    }
    if (addLexicalizedRule && f.rule.phraseTableName.equals(ptName)) {
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      String featureString = FEATURE_NAME + ":" + String.format("%s>%s", sourcePhrase, targetPhrase);
      features.add(new FeatureValue<String>(featureString, 1.0));
    }
    if (addClassBasedRule && f.rule.phraseTableName.equals(ptName)) {
      StringBuilder sb = new StringBuilder();
      for (IString token : f.sourcePhrase) {
        if (sb.length() > 0) sb.append("-");
        String tokenClass = sourceMap.get(token).toString();
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : f.targetPhrase) {
        if (seenFirst) sb.append("-");
        String tokenClass = targetMap.get(token).toString();
        sb.append(tokenClass);
        seenFirst = true;
      }
      String featureString = FEATURE_NAME + ":" + sb.toString();
      features.add(new FeatureValue<String>(featureString, 1.0));
    }
    return features;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
