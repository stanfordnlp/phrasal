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
	 * 
	 */
  LMState score(Sequence<T> sequence);

  /**
	 * 
	 */
  T getStartToken();

  /**
	 * 
	 */
  T getEndToken();

  /**
	 * 
	 */
  String getName();

  /**
	 * 
	 */
  int order();
}
