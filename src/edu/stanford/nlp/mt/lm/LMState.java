package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;

/**
 * State returned by a language model query.
 * 
 * @author Spence Green
 *
 * @param <TK>
 */
public abstract class LMState extends FeaturizerState {
  
  protected double score;
  
  public double getScore() { return score; };
  
  public abstract int length();
}
