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
public interface PhraseGenerator<TK> {
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, int translationId);
}
