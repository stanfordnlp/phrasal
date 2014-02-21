package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import edu.stanford.nlp.util.Pair;

/**
 * Target rule boundary bigrams.
 * 
 * @author Spence Green
 *
 */
public class TargetClassBigramBoundary1 extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_NAME = "TGTCBND";

  private final TargetClassMap targetMap = TargetClassMap.getInstance();
  private final boolean addDomainFeatures;
  private Map<Integer, Pair<String, Integer>> sourceIdInfoMap;
  
  // Thang Jan14
  private final int numMappings = targetMap.getNumMappings();
  private final List<IString> startEdges;
  private final List<IString> endEdges;
  private final int DEBUG_OPT = 1; // Thang Jan14: >0 print debugging message
  
  public TargetClassBigramBoundary1() {
    this.addDomainFeatures = false;
    
    // Thang Jan14
    startEdges = new ArrayList<IString>();
    endEdges = new ArrayList<IString>();
    for (int i = 0; i < numMappings; i++) {
      startEdges.add(TokenUtils.START_TOKEN);
      endEdges.add(TokenUtils.END_TOKEN);
    }  
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetClassBigramBoundary1(String...args) {
    Properties options = SparseFeatureUtils.argsToProperties(args);
    this.addDomainFeatures = options.containsKey("domainFile");
//    if (addDomainFeatures) {
//      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
//    }
    
    // Thang Jan14
    startEdges = new ArrayList<IString>();
    endEdges = new ArrayList<IString>();
    for (int i = 0; i < numMappings; i++) {
      startEdges.add(TokenUtils.START_TOKEN);
      endEdges.add(TokenUtils.END_TOKEN);
    }
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

    List<IString> leftEdges;
    if (priorState == null){
      leftEdges = startEdges;
    } else {
      leftEdges = priorState.classIdList;
    }
    
    Pair<String,Integer> genreInfo = addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId) ? 
        sourceIdInfoMap.get(f.sourceInputId) : null;
    final String genre = genreInfo == null ? null : genreInfo.first();
    
    // Detect this phrase
    if (DEBUG_OPT>0){
      System.err.println("# target bigram");
    }
    if (f.targetPhrase != null && f.targetPhrase.size() > 0) {
      List<IString> rightEdges = targetMap.getList(f.targetPhrase.get(0));
      for (int i = 0; i < numMappings; i++) {
        String featureString = String.format("%s%d:%s-%s", FEATURE_NAME, i, leftEdges.get(i), rightEdges.get(i));
        if (DEBUG_OPT>0){
          System.err.println("  " + featureString);
        }
        
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (genre != null) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
        }  
      }
      
      
      f.setState(this, new BoundaryState(rightEdges));
    } else {
      // Deletion rule, so state is the same as the last application.
      f.setState(this, new BoundaryState(leftEdges));
    }
    
    // Detect done
    if (f.done && f.targetPhrase != null && f.targetPhrase.size() > 0) {
      leftEdges = targetMap.getList(f.targetPhrase.get(f.targetPhrase.size()-1));
      
      for (int i = 0; i < numMappings; i++) {
        String featureString = String.format("%s:%s-%s", FEATURE_NAME, leftEdges.get(i), TokenUtils.END_TOKEN);
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (DEBUG_OPT>0){
          System.err.println("  " + featureString);
        }
        
        if (genre != null) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
        }
      }
      f.setState(this, new BoundaryState(endEdges));
    }
    return features;
  }
  
  private static class BoundaryState extends FeaturizerState {

    private final List<IString> classIdList;

    public BoundaryState(List<IString> classIdList) {
      this.classIdList = classIdList;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof BoundaryState)) {
        return false;
      } else {
        BoundaryState o = (BoundaryState) other;
        
        boolean isEqual = true;
        if (classIdList.size()==o.classIdList.size()){
          for (int i = 0; i < classIdList.size(); i++) {
            if(!classIdList.get(i).equals(o.classIdList.get(i))){
              isEqual = false;
              break;
            }
          }
        } else {
          isEqual = false;
        }
        return isEqual;
      }
    }

    @Override
    public int hashCode() {
      int hashCode = 0;
      for (IString classId : classIdList) {
        hashCode = hashCode << 2 + classId.hashCode();
      }
      return hashCode;
    }
  }
}
