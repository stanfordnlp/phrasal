package edu.berkeley.nlp.lm.cache;

import java.util.Arrays;

import edu.berkeley.nlp.lm.ContextEncodedNgramLanguageModel.LmContextInfo;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;

final class ContextEncodedDirectMappedLmCache implements ContextEncodedLmCache
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final int[] cachedWord;

	private final long[] cachedContextOffset;

	private final int[] cachedContextOrder;

	private final long[] cachedOutputContextPrefix;

	private final int[] cachedOutputContextOrder;

	private final float[] cachedProb;

	private final int cacheSize;

	public ContextEncodedDirectMappedLmCache(final int cacheBits) {
		cacheSize = (1 << cacheBits) - 1;
		cachedWord = new int[cacheSize];
		cachedContextOffset = new long[cacheSize];
		cachedProb = new float[cacheSize];
		cachedContextOrder = new int[cacheSize];
		cachedOutputContextOrder = new int[cacheSize];
		cachedOutputContextPrefix = new long[cacheSize];
		Arrays.fill(cachedProb, Float.NaN);
		Arrays.fill(cachedWord, -1);
	}

	@Override
	public float getCached(final long contextOffset, final int contextOrder, final int word, final int hash, @OutputParameter final LmContextInfo outputPrefix) {

		final float f = cachedProb[hash];
		if (!Float.isNaN(f)) {
			final int cachedWordHere = cachedWord[hash];
			if (cachedWordHere != -1 && equals(contextOffset, contextOrder, word, cachedContextOffset[hash], cachedWordHere, cachedContextOrder[hash])) {
				if (outputPrefix != null) {
					outputPrefix.order = cachedOutputContextOrder[hash];
					outputPrefix.offset = cachedOutputContextPrefix[hash];
				}
				return f;
			}
		}
		return Float.NaN;
	}

	private boolean equals(final long contextOffset, final int contextOrder, final int word, final long cachedOffsetHere, final int cachedWordHere,
		final int cachedOrderHere) {
		return word == cachedWordHere && contextOrder == cachedOrderHere && contextOffset == cachedOffsetHere;
	}

	@Override
	public void putCached(final long contextOffset, final int contextOrder, final int word, final float score, final int hash,
		@OutputParameter final LmContextInfo outputPrefix) {

		cachedWord[hash] = word;
		cachedProb[hash] = score;
		cachedContextOffset[hash] = contextOffset;
		cachedContextOrder[hash] = contextOrder;
		if (outputPrefix != null) {
			cachedOutputContextOrder[hash] = outputPrefix.order;
			cachedOutputContextPrefix[hash] = outputPrefix.offset;
		}
	}

	@Override
	@SuppressWarnings("ucd")
	public int capacity() {
		return cacheSize;
	}
}