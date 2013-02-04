package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
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
  List<ConcreteTranslationOption<TK,FV>> filterOptions(
      List<ConcreteTranslationOption<TK,FV>> optionList);

  /**
	 * 
	 */
  boolean allowableContinuation(Featurizable<TK, FV> featurizable,
      ConcreteTranslationOption<TK,FV> option);

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
