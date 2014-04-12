package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;

/**
 * Result of a JointNPLM query.
 * 
 * @author Thang Luong
 *
 */
public class NNLMState extends FeaturizerState {
  private double score;
  private final int[] ngramIds; // srcIds followed by tgtIds
  private final int tgtOrder; // we only care the last (tgtOrder-1) numbers in ngramIds
  private final int hashCode;

  public NNLMState(double score, int[] ngramIds, int tgtOrder) {
    this.score = score;
    this.ngramIds = ngramIds;
    this.tgtOrder = tgtOrder;
    
    // Unwrapped call to Arrays.hashCode
    int result = 1;
    
    // we only care about the last (tgtOrder-1) indices
    for (int i = ngramIds.length-tgtOrder+1; i<ngramIds.length; ++i) {
        result = 31 * result + ngramIds[i];
    }
    this.hashCode = result;
  }
  
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof NNLMState)) {
      return false;
    } else {
      NNLMState otherState = (NNLMState) other;
      if (this.tgtOrder != otherState.tgtOrder) {
        return false;
      }
      
      // only care about the last (tgtOrder-1) numbers
      for (int i = ngramIds.length-tgtOrder+1; i<ngramIds.length; ++i) {
        if (ngramIds[i] != otherState.ngramIds[i]) {
          return false;
        }
      }
      return true;
    }
  }
  
  public double getScore(){
    return score;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(String.format("[", score));
    for (int i = ngramIds.length-tgtOrder+1; i<ngramIds.length; ++i) {
      if(i==(ngramIds.length-1)){
        sb.append(ngramIds[i] + "]");
      } else {
        sb.append(ngramIds[i] + ", ");
      }
    }
    
    return sb.toString();
  }
}
