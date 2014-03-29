/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import cern.colt.Arrays;

/**
 * Copy from KenLMState.
 *  
 * @author Thang Luong
 *
 */
public class NPLMState extends LMState {
  protected final int[] state;
  protected final int stateLength;
  protected int hashCode;

  public NPLMState(double score, int[] state, int stateLength) {
    this.score = score;
    this.state = state;
    this.stateLength = stateLength;
    
    // Unwrapped call to Arrays.hashCode
    int result = 1;
    for (int i = 0; i < stateLength; ++i) {
        result = 31 * result + state[i];
    }
    this.hashCode = result;
  }
  
  public void setScore(double score){
  	this.score = score;
  }
  
  /**
   * Getter for KenLM.
   * 
   * @return
   */
  public int[] getState() { return state; }
  
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof NPLMState)) {
      return false;
    } else {
      NPLMState otherState = (NPLMState) other;
      
      if (this.stateLength != otherState.stateLength) {
        return false;
      }
      for (int i = 0; i < stateLength; ++i) {
        if (state[i] != otherState.state[i]) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public int length() {
    return stateLength;
  }
  
  @Override
  public String toString() {
    return String.format("score=%.6f,state=%s", score, Arrays.toString(state));
  }
}
