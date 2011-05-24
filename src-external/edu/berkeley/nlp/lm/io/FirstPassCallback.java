package edu.berkeley.nlp.lm.io;

import java.util.List;
import java.util.Map.Entry;

import edu.berkeley.nlp.lm.array.LongArray;
import edu.berkeley.nlp.lm.collections.Counter;
import edu.berkeley.nlp.lm.collections.Indexer;
import edu.berkeley.nlp.lm.map.ConfigOptions;
import edu.berkeley.nlp.lm.util.Logger;

/**
 * Reader callback which adds n-grams to an NgramMap
 * 
 * @author adampauls
 * 
 * @param <V>
 *            Value type
 */
public final class FirstPassCallback<V extends Comparable<V>> implements LmReaderCallback<V>
{

	int warnCount = 0;

	Counter<V> valueCounter;

	private Indexer<V> valueIndexer;

	LongArray[] numNgramsForEachWord;

	private final ConfigOptions opts;

	private final boolean reverseTrie = false;
	
	public FirstPassCallback(final ConfigOptions opts) {
		this.valueCounter = new Counter<V>();
		this.opts = opts;
	}

	@Override
	public void call(final int[] ngram, final V v, final String words) {
		valueCounter.incrementCount(v, 1);
		final LongArray ngramOrderCounts = numNgramsForEachWord[ngram.length - 1];
		final int word = reverseTrie ? ngram[0] : ngram[ngram.length - 1];
		if (word >= ngramOrderCounts.size()) {

			ngramOrderCounts.setAndGrowIfNeeded(word, 1);
		} else {
			ngramOrderCounts.set(word, ngramOrderCounts.get(word) + 1);
		}

	}

	@Override
	public void handleNgramOrderFinished(final int order) {
	}

	@Override
	public void cleanup() {
		Logger.startTrack("Cleaning up values");
		valueIndexer = new Indexer<V>();
		for (final Entry<V, Double> entry : valueCounter.getEntriesSortedByDecreasingCount()) {
			valueIndexer.add(entry.getKey());
		}
		Logger.logss("Found " + valueIndexer.size() + " unique counts");

		valueCounter = null;
		Logger.endTrack();

	}

	public Indexer<V> getIndexer() {
		return valueIndexer;

	}

	@Override
	public void initWithLengths(final List<Long> numNGrams) {
		final long numWords = numNGrams.get(0);
		numNgramsForEachWord = new LongArray[numNGrams.size()];
		for (int ngramOrder = 0; ngramOrder < numNgramsForEachWord.length; ++ngramOrder) {
			numNgramsForEachWord[ngramOrder] = LongArray.StaticMethods.newLongArray(numNGrams.get(ngramOrder), numWords, numWords);
		}
	}

	@Override
	public boolean ignoreNgrams() {
		return false;
	}

	public LongArray[] getNumNgramsForEachWord() {
		return numNgramsForEachWord;
	}
}