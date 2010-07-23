package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author Daniel Cer
 *
 * @param <TK>
 */
public interface PhraseGenerator<TK> extends Cloneable {
	/**
	 * 
	 */
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId);
	
	public Object clone() throws CloneNotSupportedException;
	
	public void setCurrentSequence(Sequence<TK> foreign, List<Sequence<TK>> tranList);
	
	public int longestForeignPhrase();
}
