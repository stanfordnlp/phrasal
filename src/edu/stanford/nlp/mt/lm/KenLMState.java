package edu.stanford.nlp.mt.lm;

import java.util.Arrays;

/**
 * Result of a KenLM query.
 * 
 * @author Spence Green
 *
 */
public class KenLMState extends LMState {

  private final int[] state;
  private final int hashCode;

  public KenLMState(double score, int[] state) {
    this.score = score;
    this.state = state;
    this.hashCode = Arrays.hashCode(state);
  }
  
  @Override
  public boolean equals(LMState other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof KenLMState)) {
      return false;
    } else {
      KenLMState otherState = (KenLMState) other;
      return Arrays.equals(this.state, otherState.state);
    }
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
}
