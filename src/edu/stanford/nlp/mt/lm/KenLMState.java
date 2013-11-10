package edu.stanford.nlp.mt.lm;

/**
 * Result of a KenLM query.
 * 
 * @author Spence Green
 *
 */
public class KenLMState extends LMState {

  private final int[] state;
  private final int stateLength;
  private final int hashCode;

  public KenLMState(double score, int[] state, int stateLength) {
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
  
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof KenLMState)) {
      return false;
    } else {
      KenLMState otherState = (KenLMState) other;
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
}
