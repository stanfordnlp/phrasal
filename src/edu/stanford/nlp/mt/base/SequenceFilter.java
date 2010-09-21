package edu.stanford.nlp.mt.base;

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
