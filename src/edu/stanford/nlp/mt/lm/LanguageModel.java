package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * Interface for language models.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public interface LanguageModel<T> {

  /**
   * Score a full sequence starting from an optional prior state, and return
   * the state needed to resume scoring if the sequence is extended.
   * 
   * startOffsetIndex is a non-negative position to begin scoring. It is usually
   * 0 (for starting at the beginning) or 1 (for starting after a begin symbol
   * like <s>).
   * 
   * @param sequence The sequence to score
   * @param startOffsetIndex index in sequence to start scoring.
   * @param priorState State from a prior call to score(). Could be null.
   * 
   * @return the language model state and the score of the sequence
   */
  LMState score(Sequence<T> sequence, int startOffsetIndex, LMState priorState);
  
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
