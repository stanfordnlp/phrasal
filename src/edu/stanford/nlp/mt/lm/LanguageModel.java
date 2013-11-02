package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public interface LanguageModel<T> {

  /**
	 * 
	 */
  double score(Sequence<T> sequence);

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

  /**
	 * 
	 */
  boolean relevantPrefix(Sequence<T> sequence);
}
