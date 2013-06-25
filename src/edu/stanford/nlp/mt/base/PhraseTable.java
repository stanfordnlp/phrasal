package edu.stanford.nlp.mt.base;

import java.util.List;

/**
 * Interface for phrase table data structures.
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public interface PhraseTable<T> {

  /**
	 * 
	 */
  List<Rule<T>> query(Sequence<T> sequence);

  /**
	 * 
	 */
  int longestSourcePhrase();

  /**
	 * 
	 */
  String getName();
}
