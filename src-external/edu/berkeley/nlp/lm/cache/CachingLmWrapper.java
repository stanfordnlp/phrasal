package edu.berkeley.nlp.lm.cache;

import edu.berkeley.nlp.lm.AbstractNgramLanguageModel;
import edu.berkeley.nlp.lm.NgramLanguageModel;

/**
 * This wrapper is <b>not</b> threadsafe. To use a cache in a multithreaded
 * environment, you should create one CachingLmWrapper per thread.
 * 
 * @author adampauls
 * 
 * @param <W>
 */
public class CachingLmWrapper<W> extends AbstractNgramLanguageModel<W>
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final LmCache cache;

	private final NgramLanguageModel<W> lm;

	public CachingLmWrapper(final NgramLanguageModel<W> lm) {
		this(lm, new DirectMappedLmCache(24));
	}

	public CachingLmWrapper(final NgramLanguageModel<W> lm, final LmCache cache) {
		super(lm.getLmOrder(), lm.getWordIndexer());
		this.cache = cache;
		this.lm = lm;

	}

	@Override
	public float getLogProb(final int[] ngram, final int startPos, final int endPos) {
		final int hash = Math.abs(hash(ngram, startPos, endPos)) % cache.capacity();
		float f = cache.getCached(ngram, startPos, endPos, hash);
		if (!Float.isNaN(f)) return f;
		f = lm.getLogProb(ngram, startPos, endPos);
		cache.putCached(ngram, startPos, endPos, f, hash);

		return f;
	}

	private static int hash(final int[] key, final int startPos, final int endPos) {
		int hashCode = 1;
		for (int i = startPos; i < endPos; ++i) {

			final int curr = key[i];
			hashCode = 13 * hashCode + curr;
		}
		return hashCode;
	}

}
