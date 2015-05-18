package edu.stanford.nlp.mt.lm;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.util.MurmurHash;

/**
 * Result of a KenLM query.
 * 
 * @author Spence Green
 *
 */
public class KenLMState extends LMState {

  private static final Logger logger = LogManager.getLogger(KenLMState.class.getName());
  
  private final int[] state;
  private final int hashCode;

  /**
   * Constructor.
   * 
   * @param score
   * @param state
   * @param stateLength
   */
  public KenLMState(double score, int[] state, int stateLength) {
    this.score = score;
    if (stateLength < state.length) {
      this.state = new int[stateLength];
      System.arraycopy(state, 0, this.state, 0, stateLength);
    } else if (stateLength == state.length) {
      this.state = state;
    } else {
      logger.error("State length mis-match: {} vs. {}", state.length, stateLength);
      throw new RuntimeException();
    }
    this.hashCode = MurmurHash.hash32(state, state.length, 1);
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
    } else if ( ! (other instanceof KenLMState)) {
      return false;
    } else {
      KenLMState otherState = (KenLMState) other;
      if (this.state.length != otherState.state.length) {
        return false;
      }
      for (int i = 0; i < this.state.length; ++i) {
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
    return state.length;
  }
  
  @Override
  public String toString() {
    return String.format("%.6f\t%s", score, Arrays.toString(state));
  }
}
