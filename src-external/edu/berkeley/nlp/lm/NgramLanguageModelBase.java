package edu.berkeley.nlp.lm;

import java.util.List;

/**
 * 
 * @author adampauls Methods shared between
 *         {@link ContextEncodedNgramLanguageModel} and
 *         {@link NgramLanguageModel}
 * 
 * @param <W>
 *            A type representing words in the language. Can be a
 *            <code>String</code>, or something more complex if needed
 */
public interface NgramLanguageModelBase<W>
{

	/**
	 * Maximum size of n-grams stored by the model.
	 * 
	 * @return
	 */
	public int getLmOrder();

	/**
	 * Each LM must have a WordIndexer which assigns integer IDs to each word W
	 * in the language.
	 * 
	 * @return
	 */
	public WordIndexer<W> getWordIndexer();

	/**
	 * Scores a complete sentence, taking appropriate care with the start- and
	 * end-of-sentence symbols.
	 * 
	 * @return
	 */
	public float scoreSentence(List<W> sentence);

}
