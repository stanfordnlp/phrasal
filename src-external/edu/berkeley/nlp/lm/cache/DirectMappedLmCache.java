package edu.berkeley.nlp.lm.cache;

import java.util.Arrays;

public final class DirectMappedLmCache implements LmCache
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final int cacheSize;

	public DirectMappedLmCache(final int cacheBits) {
		cacheSize = (1 << cacheBits) - 1;
		cachedKey = new int[cacheSize][];
		cachedVal = new float[cacheSize];
		Arrays.fill(cachedVal, Float.NaN);
	}

	private final int[][] cachedKey;

	private final float[] cachedVal;

	@SuppressWarnings("ucd")
	public static double getCacheHitRate() {
		if (cacheHits + cacheMisses == 0.0) return 0.0;
		return (double) cacheHits / (cacheHits + cacheMisses);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.cache.LmCache#getCached(int[], int, int, int)
	 */
	@Override
	public float getCached(final int[] ngram, final int startPos, final int endPos, final int shortHash) {

		final float f = cachedVal[shortHash];
		if (!Float.isNaN(f)) {
			final int[] cachedNgram = cachedKey[shortHash];
			if (cachedNgram != null && equals(ngram, startPos, endPos, cachedNgram)) {
				cacheHits++;
				return f;
			}
		}
		return Float.NaN;
	}

	private boolean equals(final int[] ngram, final int startPos, final int endPos, final int[] cachedNgram) {
		if (cachedNgram.length != endPos - startPos) return false;
		for (int i = startPos; i < endPos; ++i) {
			if (cachedNgram[i - startPos] != ngram[i]) return false;
		}
		return true;
	}

	private static int cacheHits = 0;

	private static int cacheMisses = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.cache.LmCache#clear()
	 */
	@Override
	public void clear() {
		Arrays.fill(cachedVal, Float.NaN);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.cache.LmCache#putCached(int[], int, int,
	 * float, int)
	 */
	@Override
	public void putCached(final int[] ngram, final int startPos, final int endPos, final float f, final int shortHash) {
		cachedKey[shortHash] = Arrays.copyOfRange(ngram, startPos, endPos);
		cachedVal[shortHash] = f;
		cacheMisses++;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.cache.LmCache#size()
	 */
	@Override
	public int capacity() {
		return cacheSize;
	}
}