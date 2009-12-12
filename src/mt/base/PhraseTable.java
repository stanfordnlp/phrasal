package mt.base;

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
	 * @param sequence
	 */
	List<TranslationOption<T>> getTranslationOptions(Sequence<T> sequence);
	
	/**
	 * 
	 */
	int longestForeignPhrase();
	
	
	/**
	 * 
	 */
	String getName();
}
