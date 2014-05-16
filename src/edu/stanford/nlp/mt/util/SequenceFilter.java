package edu.stanford.nlp.mt.util;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public interface SequenceFilter<TK> {

  /**
	 * 
	 */
  boolean accepts(Sequence<TK> sequence);
}
