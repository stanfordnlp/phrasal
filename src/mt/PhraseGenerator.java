package mt;

import java.util.*;

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
