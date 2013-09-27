package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.util.Generics;

/**
 * Target boundary bigrams.
 * 
 * @author Spence Green
 *
 */
public class TargetClassBigramBoundary implements DerivationFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCBND";
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    if (! TargetClassMap.isLoaded()) {
      throw new RuntimeException("You must enable the " + Phrasal.TARGET_CLASS_MAP + " decoder option");
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    // Detect last phrase
    String leftEdge = "<S>";
    for (Featurizable<IString, String> fPrior = f.prior; fPrior != null; fPrior = fPrior.prior) {
      if (fPrior.targetPhrase != null && fPrior.targetPhrase.size() > 0) {
        leftEdge = TargetClassMap.get(fPrior.targetPhrase.get(fPrior.targetPhrase.size()-1)).toString();
        break;
      }
    }
    
    // Detect this phrase
    if (f.targetPhrase != null && f.targetPhrase.size() > 0) {
      String rightEdge = TargetClassMap.get(f.targetPhrase.get(0)).toString();
      features.add(new FeatureValue<String>(String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge), 1.0));      
    }
    
    // Detect done
    if (f.done && f.targetPhrase != null && f.targetPhrase.size() > 0) {
      leftEdge = TargetClassMap.get(f.targetPhrase.get(f.targetPhrase.size()-1)).toString();
      String rightEdge = "</S>";
      features.add(new FeatureValue<String>(String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge), 1.0));  
    }
    return features;
  }
}
