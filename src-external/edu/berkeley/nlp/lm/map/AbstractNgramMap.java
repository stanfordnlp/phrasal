package edu.berkeley.nlp.lm.map;

import java.io.Serializable;
import java.util.Arrays;

import edu.berkeley.nlp.lm.values.ValueContainer;

public abstract class AbstractNgramMap<T> implements NgramMap<T>, Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected final ValueContainer<T> values;

	protected final ConfigOptions opts;

	protected AbstractNgramMap(final ValueContainer<T> values, final ConfigOptions opts) {
		this.values = values;
		this.opts = opts;
	}

	protected static boolean equals(final int[] ngram, final int startPos, final int endPos, final int[] cachedNgram) {
		if (cachedNgram.length != endPos - startPos) return false;
		for (int i = 0; i < endPos - startPos; ++i) {
			if (ngram[startPos + i] != cachedNgram[i]) return false;
		}
		return true;
	}

	protected static int[] getSubArray(final int[] ngram, final int startPos, final int endPos) {
		return Arrays.copyOfRange(ngram, startPos, endPos);

	}

	protected static boolean containsOutOfVocab(final int[] ngram, final int startPos, final int endPos) {
		for (int i = startPos; i < endPos; ++i) {
			if (ngram[i] < 0) return true;
		}
		return false;
	}

	@Override
	public ValueContainer<T> getValues() {
		return values;
	}

}