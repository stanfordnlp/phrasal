package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Indicator features for each rule in a derivation.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class DiscriminativePhraseTable2 implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "DPT";

  private final boolean addLexicalizedRule;
  private final boolean addClassBasedRule;

  public DiscriminativePhraseTable2() {
    this.addLexicalizedRule = true;
    this.addClassBasedRule = false;
  }

  public DiscriminativePhraseTable2(String... args) {
    this.addLexicalizedRule = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
    this.addClassBasedRule = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    
    if (addClassBasedRule) {
      if (! SourceClassMap.isLoaded()) {
        throw new RuntimeException("You must enable the " + Phrasal.SOURCE_CLASS_MAP + " decoder option");
      }
      if (! TargetClassMap.isLoaded()) {
        throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
      }
    }
  }

  @Override
  public void initialize() {
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (addLexicalizedRule) {
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      String ruleString = String.format("%s>%s", sourcePhrase, targetPhrase);
      features.add(new FeatureValue<String>(FEATURE_NAME + ":" + ruleString, 1.0));        
    }
    if (addClassBasedRule) {
      StringBuilder sb = new StringBuilder();
      for (IString token : f.sourcePhrase) {
        if (sb.length() > 0) sb.append("-");
        String tokenClass = SourceClassMap.get(token).toString();
        sb.append(tokenClass);
      }
      sb.append(">");
      boolean seenFirst = false;
      for (IString token : f.targetPhrase) {
        if (seenFirst) sb.append("-");
        String tokenClass = TargetClassMap.get(token).toString();
        sb.append(tokenClass);
        seenFirst = true;
      }
      features.add(new FeatureValue<String>(FEATURE_NAME + ":" + sb.toString(), 1.0));
    }
    return features;
  }
}
