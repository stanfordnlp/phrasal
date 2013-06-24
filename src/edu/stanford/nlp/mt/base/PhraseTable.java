package edu.stanford.nlp.mt.base;

import java.util.*;

/**
 * 
 * @author danielcer
 * 
 * @param <T>
 */
public interface PhraseTable<T> {

  /**
	 * 
	 */
  List<Rule<T>> getTranslationOptions(Sequence<T> sequence);

  /**
	 * 
	 */
  int longestSourcePhrase();

  /**
	 * 
	 */
  String getName();
}
