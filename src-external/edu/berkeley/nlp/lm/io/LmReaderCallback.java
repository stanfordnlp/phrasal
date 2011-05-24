package edu.berkeley.nlp.lm.io;

import java.util.List;

/**
 * Callback that is called for each n-gram in the collection
 * 
 * @author adampauls
 * 
 * @param <V>
 *            Value type for each n-gram (either count of prob/backoff)
 */
public interface LmReaderCallback<V>
{

	/**
	 * Called initially with a list of how many n-grams will appear for each
	 * order.
	 * 
	 * @param numNGrams
	 *            maps n-gram orders to number of n-grams (i.e. numNGrams.get(0)
	 *            is the number of unigrams)
	 */
	public void initWithLengths(List<Long> numNGrams);

	/**
	 * Called for each n-gram
	 * 
	 * @param ngram
	 *            The integer representation of the words as given by the
	 *            provided WordIndexer
	 * @param value
	 *            The value of the n-gram
	 * @param words
	 *            The string representation of the n-gram (space separated)
	 */
	public void call(int[] ngram, V value, String words);

	/**
	 * Called when all n-grams of a given order are finished
	 * 
	 * @param order
	 */
	public void handleNgramOrderFinished(int order);

	/**
	 * Called once all reading is done.
	 */
	public void cleanup();

	/**
	 * Whether this call back is interested in the n-grams or not. If not, then
	 * parsing can be speed up.
	 * 
	 * @return
	 */
	public boolean ignoreNgrams();

}