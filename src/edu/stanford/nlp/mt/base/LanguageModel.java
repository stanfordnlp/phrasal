package edu.stanford.nlp.mt.base;

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
  boolean releventPrefix(Sequence<T> sequence);
}
