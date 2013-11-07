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
