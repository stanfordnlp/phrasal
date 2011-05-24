package edu.berkeley.nlp.lm.map;

import java.io.Serializable;

import edu.berkeley.nlp.lm.array.LongArray;
import edu.berkeley.nlp.lm.util.Logger;
import edu.berkeley.nlp.lm.util.Annotations.PrintMemoryCount;

/**
 * Low-level hash map which stored context-encoded parent pointers in a trie.
 * 
 * @author adampauls
 * 
 */
final class HashMap implements Serializable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@PrintMemoryCount
	final LongArray keys;

	@PrintMemoryCount
	final long[] wordRangesLow;

	@PrintMemoryCount
	final long[] wordRangesHigh;

	long numFilled = 0;

	long maxWord = 0;

	private final boolean reverseTrie;

	private static final int EMPTY_KEY = -1;

	public HashMap(final LongArray numNgramsForEachWord, final double maxLoadFactor) {
		final long numWords = numNgramsForEachWord.size();
		wordRangesLow = new long[(int) numWords];
		wordRangesHigh = new long[(int) numWords];
		long totalNumNgrams = setWordRanges(numNgramsForEachWord, maxLoadFactor, numWords);
		keys = LongArray.StaticMethods.newLongArray(totalNumNgrams, totalNumNgrams, totalNumNgrams);
		Logger.logss("No word key size " + totalNumNgrams);
		keys.fill(EMPTY_KEY, totalNumNgrams);
		reverseTrie = false;
		numFilled = 0;
	}

	public long put(final long index, final long putKey) {
		final int firstWordOfNgram = HashNgramMap.wordOf(putKey);
		final long rangeStart = wordRangesLow[firstWordOfNgram];
		final long rangeEnd = wordRangesHigh[firstWordOfNgram];
		long searchKey = getKey(index);
		long i = index;
		boolean goneAroundOnce = false;
		while (searchKey != EMPTY_KEY && searchKey != putKey) {
			++i;
			if (i >= rangeEnd) {
				if (goneAroundOnce) throw new RuntimeException("Infinite loop when trying to add to HashMap");
				i = rangeStart;
				goneAroundOnce = true;
			}
			searchKey = getKey(i);
		}

		if (searchKey == EMPTY_KEY) setKey(i, putKey);
		numFilled++;
		maxWord = Math.max(maxWord, firstWordOfNgram);

		return i;
	}

	/**
	 * @param numNgramsForEachWord
	 * @param maxLoadFactor
	 * @param numWords
	 * @return
	 */
	private long setWordRanges(final LongArray numNgramsForEachWord, final double maxLoadFactor, final long numWords) {
		long currStart = 0;
		for (int w = 0; w < numWords; ++w) {
			wordRangesLow[w] = currStart;
			final long numNgrams = numNgramsForEachWord.get(w);
			currStart += numNgrams <= 3 ? numNgrams : Math.round(numNgrams * 1.0 / maxLoadFactor);
			wordRangesHigh[w] = currStart;

		}
		return currStart;
	}

	private long getKey(final long index) {
		return keys.get(index);
	}

	private void setKey(final long index, final long putKey) {
		assert keys.get(index) == EMPTY_KEY;
		final long contextOffset = HashNgramMap.contextOffsetOf(putKey);
		assert contextOffset >= 0;
		keys.set(index, contextOffset);

	}

	final long getIndexImplicity(final long contextOffset, final int word, final long startIndex) {
		final LongArray localKeys = keys;
		final long rangeStart = wordRangesLow[word];
		final long rangeEnd = wordRangesHigh[word];
		assert startIndex >= rangeStart;
		assert startIndex < rangeEnd;
		long i = startIndex;
		boolean goneAroundOnce = false;
		while (true) {
			if (i == rangeEnd) {
				if (goneAroundOnce) return -1L;
				i = rangeStart;
				goneAroundOnce = true;
			}
			final long searchKey = localKeys.get(i);
			if (searchKey == contextOffset) {//
				return i;
			}
			if (searchKey == EMPTY_KEY) {//
				return -1L;
			}
			++i;

		}
	}

	public final long getIndexImplicitly(final int[] ngram, final long index, final int startPos, final int endPos, final HashMap[] maps) {
		final LongArray localKeys = keys;
		final int firstWordOfNgram = reverseTrie ? ngram[startPos] : ngram[endPos - 1];
		final long rangeStart = wordRangesLow[firstWordOfNgram];
		final long rangeEnd = wordRangesHigh[firstWordOfNgram];
		assert index >= rangeStart;
		assert index < rangeEnd;
		long i = index;
		boolean goneAroundOnce = false;
		while (true) {
			if (i == rangeEnd) {
				if (goneAroundOnce) return -1L;
				i = rangeStart;
				goneAroundOnce = true;
			}
			final long searchKey = localKeys.get(i);
			if (searchKey == EMPTY_KEY) {//
				return -1L;
			}
			if (implicitSuffixEquals(searchKey, ngram, startPos, endPos, maps, reverseTrie)) { //
				return i;
			}
			++i;

		}
	}

	long getCapacity() {
		return keys.size();
	}

	double getLoadFactor() {
		return (double) numFilled / getCapacity();
	}

	private static final boolean implicitSuffixEquals(final long contextOffset_, final int[] ngram, final int startPos, final int endPos,
		final HashMap[] localMaps, final boolean reverse) {
		return reverse ? implicitSuffixEqualsReverse(contextOffset_, ngram, startPos + 1, endPos, localMaps) : implicitSuffixEqualsForward(contextOffset_,
			ngram, startPos, endPos - 1, localMaps);
	}

	private static final boolean implicitSuffixEqualsForward(final long contextOffset_, final int[] ngram, final int startPos, final int endPos,
		final HashMap[] localMaps) {
		long contextOffset = contextOffset_;
		for (int pos = endPos - 1; pos >= startPos; --pos) {
			final HashMap suffixMap = localMaps[pos - startPos];
			final int firstSearchWord = ngram[pos];
			if (firstSearchWord >= suffixMap.wordRangesLow.length) return false;
			final long rangeStart = suffixMap.wordRangesLow[firstSearchWord];
			if (contextOffset < rangeStart) return false;
			final long rangeEnd = suffixMap.wordRangesHigh[firstSearchWord];
			if (contextOffset >= rangeEnd) return false;
			if (pos == startPos) return true;
			final long currKey = suffixMap.getKey(contextOffset);
			contextOffset = HashNgramMap.contextOffsetOf(currKey);
		}
		return true;

	}

	private static final boolean implicitSuffixEqualsReverse(final long contextOffset_, final int[] ngram, final int startPos, final int endPos,
		final HashMap[] localMaps) {
		long contextOffset = contextOffset_;
		for (int pos = startPos; pos < endPos; ++pos) {
			final HashMap suffixMap = localMaps[endPos - pos - 1];
			final int firstSearchWord = ngram[pos];
			if (firstSearchWord >= suffixMap.wordRangesLow.length) return false;
			final long rangeStart = suffixMap.wordRangesLow[firstSearchWord];
			if (contextOffset < rangeStart) return false;
			final long rangeEnd = suffixMap.wordRangesHigh[firstSearchWord];
			if (contextOffset >= rangeEnd) return false;
			if (pos == endPos - 1) return true;
			final long currKey = suffixMap.getKey(contextOffset);
			contextOffset = HashNgramMap.contextOffsetOf(currKey);
		}
		return true;

	}

	public long processHash(final long hash_, final int word) {

		long hash = hash_;
		if (hash < 0) hash = -hash;
		if (wordRangesLow == null) return (int) (hash % getCapacity());
		if (word >= wordRangesLow.length) return -1;
		final long startOfRange = wordRangesLow[word];
		final long numHashPositions = wordRangesHigh[word] - startOfRange;
		if (numHashPositions == 0) return -1;
		hash = (hash % numHashPositions);
		return hash + startOfRange;

	}
}