package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Constrains the output space of the decoder for force
 * decoding.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public interface ConstrainedOutputSpace<TK, FV> {

  /**
   * Filter a list of translation rules to a set of target
   * sequences.
   * 
   * @param ruleList
   * @return
   */
  public List<ConcreteRule<TK,FV>> filter(List<ConcreteRule<TK,FV>> ruleList);

  /**
   * Returns true if the derivation created by the concatenation of
   * the arguments is an allowable sequence.
   * 
   * @param featurizable
   * @param rule
   * @return
   */
  public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK,FV> rule);

  /**
   * Returns true if the featurizable contains an allowable translation.
   * 
   * @param featurizable
   * @return
   */
  public boolean allowableFinal(Featurizable<TK, FV> featurizable);

  /**
   * Return all allowable target sequences.
   * 
   * @return
   */
  public List<Sequence<TK>> getAllowableSequences();
}
