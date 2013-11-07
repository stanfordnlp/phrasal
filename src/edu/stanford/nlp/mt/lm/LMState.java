package edu.stanford.nlp.mt.lm;

/**
 * State returned by a language model query.
 * 
 * @author Spence Green
 *
 * @param <TK>
 */
public abstract class LMState {
  
  protected double score;
  
  public abstract boolean equals(LMState other);
  
  public abstract int hashCode();
  
  public double getScore() { return score; };
  
  public abstract int length();
}
