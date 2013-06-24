package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public interface ConstrainedOutputSpace<TK, FV> {

  /**
	 * 
	 */
  List<ConcreteRule<TK,FV>> filterOptions(
      List<ConcreteRule<TK,FV>> optionList);

  /**
	 * 
	 */
  boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteRule<TK,FV> option);

  /**
	 * 
	 */
  boolean allowablePartial(Featurizable<TK, FV> featurizable);

  /**
	 * 
	 */
  boolean allowableFinal(Featurizable<TK, FV> featurizable);

  /**
	 * 
	 */
  public List<Sequence<TK>> getAllowableSequences();
}
