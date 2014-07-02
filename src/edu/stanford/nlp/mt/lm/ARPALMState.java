package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Result of an ARPALanguageModel query.
 * 
 * @author Spence Green
 *
 */
public class ARPALMState extends LMState {

  private final Sequence<IString> state;
  private final int hashCode;

  public ARPALMState(double score, Sequence<IString> state) {
    this.score = score;
    this.state = state;
    this.hashCode = state.hashCode();
  }
  
  public ARPALMState(double score, ARPALMState state) {
    this.score = score;
    this.state = state.state;
    this.hashCode = state.hashCode;
  }
  
  public Sequence<IString> getState() { return state; }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if ( ! (other instanceof ARPALMState)) {
      return false;
    } else {
      ARPALMState otherState = (ARPALMState) other;
      return this.state.equals(otherState.state);
    }
  }

  @Override
  public int length() {
    return state.size();
  }
  
  @Override
  public String toString() {
    return String.format("%.6f\t%s", score, state.toString());
  }
}
