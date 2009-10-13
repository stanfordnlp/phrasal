package mt.decoder.util;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.Sequence;

/**
 * 
 * @author Daniel Cer
 *
 * @param <TK>
 */
public interface PhraseGenerator<TK> extends Cloneable {
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId);
	
	public PhraseGenerator<TK> clone();
	
	public void setCurrentSequence(Sequence<TK> foreign, List<Sequence<TK>> tranList);
	
	public int longestForeignPhrase();
}
