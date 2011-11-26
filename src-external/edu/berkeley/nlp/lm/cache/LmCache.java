package edu.berkeley.nlp.lm.cache;

import java.io.Serializable;

public interface LmCache extends Serializable
{

	/**
	 * Should return Float.NaN if the requested n-gram is not in the cache
	 * 
	 * @param ngram
	 * @param startPos
	 * @param endPos
	 * @param shortHash
	 */
	public float getCached(int[] ngram, int startPos, int endPos, int hash);

	public void clear();

	public void putCached(int[] ngram, int startPos, int endPos, float f, int hash);

	/**
	 * How n-grams can be cached (at most).
	 * 
	 */
	public int capacity();

}