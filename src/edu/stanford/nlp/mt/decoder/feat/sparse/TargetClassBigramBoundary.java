package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Target rule boundary bigrams.
 * 
 * @author Spence Green
 *
 */
public class TargetClassBigramBoundary extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCBND";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  private final boolean addDomainFeatures;

  /**
   * Constructor.
   */
  public TargetClassBigramBoundary() {
    this.addDomainFeatures = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetClassBigramBoundary(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFeature");
  }

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
    
    final String genre = addDomainFeatures && f.sourceInputProperties.containsKey(InputProperty.Domain)
        ? (String) f.sourceInputProperties.get(InputProperty.Domain) : null;
    
    // Detect this phrase
    if (f.targetPhrase != null && f.targetPhrase.size() > 0) {
      IString rightEdge = targetMap.get(f.targetPhrase.get(0));
      String featureString = String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge);
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
      
      f.setState(this, new BoundaryState(rightEdge));
    } else {
      // Deletion rule, so state is the same as the last application.
      f.setState(this, new BoundaryState(leftEdge));
    }
    
    // Detect done
    if (f.done && f.targetPhrase != null && f.targetPhrase.size() > 0) {
      leftEdge = targetMap.get(f.targetPhrase.get(f.targetPhrase.size()-1));
      IString rightEdge = TokenUtils.END_TOKEN;
      String featureString = String.format("%s:%s-%s", FEATURE_NAME, leftEdge, rightEdge);
      features.add(new FeatureValue<String>(featureString, 1.0));
      if (genre != null) {
        features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
      }
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
