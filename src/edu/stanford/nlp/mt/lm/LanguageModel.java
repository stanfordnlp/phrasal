package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * Interface for MT language models.
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public interface LanguageModel<T> {

  /**
	 * Language model query for the input sequence.
	 */
  LMState score(Sequence<T> sequence);

  /**
   * Score a full sequence starting from a prior state, and return
   * the state of the last scoring call.
   * 
   * @param sequence
   * @param startIndex index in sequence to start scoring.
   * @param priorState
   * @return
   */
  LMState score(Sequence<T> sequence, int startIndex, LMState priorState);
  
  /**
	 * @return the LM-specific start token.
	 */
  T getStartToken();

  /**
	 * @return the LM-specific end token.
	 */
  T getEndToken();

  /**
	 * @return Return the name of the language model.
	 */
  String getName();

  /**
   * @return the order of the language model.
   */
  int order();
}
