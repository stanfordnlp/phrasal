package mt;

import java.util.List;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface ConstrainedOutputSpace<TK,FV> {
	
	/**
	 * 
	 * @param optionList
	 * @return
	 */
	List<ConcreteTranslationOption<TK>> filterOptions(List<ConcreteTranslationOption<TK>> optionList);
	
	/**
	 * 
	 * @param featurizable
	 * @param nextPhrase
	 * @return
	 */
	boolean allowableContinuation(Featurizable<TK,FV> featurizable, Sequence<TK> nextPhrase);
	
	/**
	 * 
	 * @param partialTranslation
	 * @return
	 */
	boolean allowablePartial(Featurizable<TK,FV> featurizable);
	
	/**
	 * 
	 * @param partialTranslation
	 * @return
	 */
	boolean allowableFinal(Featurizable<TK,FV> featurizable);
	
}
