package edu.stanford.nlp.mt.decoder.efeat;

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
 * Discriminative phrase table based on word classes.
 * 
 * @author Spence Green
 *
 */
public class ClassBasedPhraseTable implements RuleFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "CLSPT";
  
  @Override
  public void initialize() {
    if (! SourceClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.SOURCE_CLASS_MAP + " decoder option");
    }
    if (! TargetClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
    }
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    StringBuilder sb = new StringBuilder();
    
    // Source side
    for (IString token : f.sourcePhrase) {
      String tokenClass = SourceClassMap.get(token).toString();
      if (sb.length() > 0) sb.append("-");
      sb.append(tokenClass);
    }
    sb.append("->");

    // Target side
    for (IString token : f.targetPhrase) {
      String tokenClass = TargetClassMap.get(token).toString();
      sb.append("-").append(tokenClass);
    }
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(String.format("%s:%s", FEATURE_NAME, sb.toString()), 1.0));
    return features;
  }
}
