package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.util.Generics;

/**
 * Target boundary bigrams.
 * 
 * @author Spence Green
 *
 */
public class TargetClassBigramBoundary extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCBND";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    // Detect last phrase
    BoundaryState priorState = f.prior == null ? null : (BoundaryState) f.prior.getState(this);
    
    IString leftEdge = priorState == null ? TokenUtils.START_TOKEN : priorState.classId;
//    for (Featurizable<IString, String> fPrior = f.prior; fPrior != null; fPrior = fPrior.prior) {
//      if (fPrior.targetPhrase != null && fPrior.targetPhrase.size() > 0) {
//        leftEdge = TargetClassMap.get(fPrior.targetPhrase.get(fPrior.targetPhrase.size()-1)).toString();
//        break;
//      }
//    }
    
    // Detect this phrase
    if (f.targetPhrase != null && f.targetPhrase.size() > 0) {
      IString rightEdge = new IString(Sentence.listToString(targetMap.get(f.targetPhrase.get(0)), true, "-"));
      features.add(new FeatureValue<String>(String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge), 1.0));      
      f.setState(this, new BoundaryState(rightEdge));
    } else {
      // Deletion rule, so state is the same as the last application.
      f.setState(this, new BoundaryState(leftEdge));
    }
    
    // Detect done
    if (f.done && f.targetPhrase != null && f.targetPhrase.size() > 0) {
      leftEdge = new IString(Sentence.listToString(targetMap.get(f.targetPhrase.get(f.targetPhrase.size()-1)), true, "-"));
      IString rightEdge = TokenUtils.END_TOKEN;
      features.add(new FeatureValue<String>(String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge), 1.0));
      f.setState(this, new BoundaryState(rightEdge));
    }
    return features;
  }
  
  private static class BoundaryState extends FeaturizerState {

    private final IString classId;

    public BoundaryState(IString classId) {
      this.classId = classId;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof BoundaryState)) {
        return false;
      } else {
        BoundaryState o = (BoundaryState) other;
        return classId.equals(o.classId);
      }
    }

    @Override
    public int hashCode() {
      return classId.hashCode();
    }
  }
}
