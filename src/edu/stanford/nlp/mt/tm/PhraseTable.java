package edu.stanford.nlp.mt.tm;

import java.util.List;

import edu.stanford.nlp.mt.util.Sequence;

/**
 * Interface for phrase table data structures.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <T>
 */
public interface PhraseTable<T> {

  /**
   * Return a set of rules for a source sequence.
   * 
   * @param sequence
   * @return
   */
  List<Rule<T>> query(Sequence<T> sequence);

  /**
   * Lookup a rule by source and target span.
   * 
   * @param sourceSequence
   * @param targetSequence
   * @return
   */
  int getId(Sequence<T> sourceSequence, Sequence<T> targetSequence);
  
  /**
   * Return the dimension of the longest source phrase.
   * 
   * @return
   */
  int maxLengthSource();

  /**
   * Return the name of the phrase table.
   * 
   * @return
   */
  String getName();
  
  /**
   * The number of rules in the phrase table.
   * 
   * @return
   */
  int size();
  
  /**
   * The lowest rule index in this phrase table. Rule indices are assumed
   * to be contiguous from this value, inclusive.
   * 
   * @return
   */
  int minRuleIndex();
}
