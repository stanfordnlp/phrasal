package mt.decoder.util;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.Featurizable;
import mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface ConstrainedOutputSpace<TK,FV> {
	
	/**
	 * 
	 */
	List<ConcreteTranslationOption<TK>> filterOptions(List<ConcreteTranslationOption<TK>> optionList);
	
	/**
	 * 
	 */
	boolean allowableContinuation(Featurizable<TK,FV> featurizable, ConcreteTranslationOption<TK> option);
	
	/**
	 * 
	 */
	boolean allowablePartial(Featurizable<TK,FV> featurizable);
	
	/**
	 * 
	 */
	boolean allowableFinal(Featurizable<TK,FV> featurizable);
	
	/**
	 * 
	 */
	public List<Sequence<TK>> getAllowableSequences();	
}
