package edu.berkeley.nlp.lm.cache;

import edu.berkeley.nlp.lm.AbstractContextEncodedNgramLanguageModel;
import edu.berkeley.nlp.lm.ContextEncodedNgramLanguageModel;
import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;

public class ContextEncodedCachingLmWrapper<T> extends AbstractContextEncodedNgramLanguageModel<T>
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private ContextEncodedLmCache contextCache;

	private final ContextEncodedNgramLanguageModel<T> lm;

	public ContextEncodedCachingLmWrapper(final ContextEncodedNgramLanguageModel<T> lm) {
		this(lm, new ContextEncodedDirectMappedLmCache(24));
	}

	public ContextEncodedCachingLmWrapper(final ContextEncodedNgramLanguageModel<T> lm, final ContextEncodedLmCache cache) {
		super(lm.getLmOrder(), lm.getWordIndexer());
		this.lm = lm;
		this.contextCache = cache;

	}

	@Override
	public float getLogProb(final long contextOffset, final int contextOrder, final int word, @OutputParameter final LmContextInfo contextOutput) {
		final int hash = Math.abs(hash(contextOffset, contextOrder, word)) % contextCache.capacity();
		float f = contextCache.getCached(contextOffset, contextOrder, word, hash, contextOutput);
		if (!Float.isNaN(f)) return f;
		f = lm.getLogProb(contextOffset, contextOrder, word, contextOutput);
		contextCache.putCached(contextOffset, contextOrder, word, f, hash, contextOutput);

		return f;
	}

	private int hash(final long contextOffset, final int contextOrder, final int word) {
		long hashCode = 1;

		hashCode = 13 * hashCode + word;
		hashCode = 13 * hashCode + contextOrder;
		hashCode = 13 * hashCode + contextOffset;

		return (int) hashCode;
	}

	@Override
	public WordIndexer<T> getWordIndexer() {
		return lm.getWordIndexer();
	}

	@Override
	public LmContextInfo getOffsetForNgram(int[] ngram, int startPos, int endPos) {
		return lm.getOffsetForNgram(ngram, startPos, endPos);
	}

}
