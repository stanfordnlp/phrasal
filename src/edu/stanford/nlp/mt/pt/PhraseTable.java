package edu.stanford.nlp.mt.pt;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

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
