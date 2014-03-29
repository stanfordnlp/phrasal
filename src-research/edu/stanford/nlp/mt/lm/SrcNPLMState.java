/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import cern.colt.Arrays;

/**
 * 
 * NPLMState with an additional piece of information, 
 * the current rule id, needed for src-conditioned NPLM.
 * 
 * @author Thang Luong
 *
 */
public class SrcNPLMState extends NPLMState {
	private final int ruleId;
	
	public SrcNPLMState(double score, int[] state, int stateLength, int ruleId) {
		super(score, state, stateLength);
		this.ruleId = ruleId;
		this.hashCode = this.hashCode*31 + ruleId;
	}
	

  public int getRuleId() { return ruleId; }
  
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof SrcNPLMState)) {
      return false;
    } else {
      SrcNPLMState otherState = (SrcNPLMState) other;
      if (this.ruleId != otherState.ruleId) {
        return false;
      }
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
  public String toString() {
    return String.format("score=%.6f,state=%s,ruleId=%d", score, Arrays.toString(state), ruleId);
  }
}
