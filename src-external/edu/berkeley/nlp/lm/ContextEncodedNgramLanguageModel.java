package edu.berkeley.nlp.lm;

import java.util.List;

import edu.berkeley.nlp.lm.collections.BoundedList;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;

/**
 * Interface for language models which expose the internal context-encoding for
 * more efficient queries. (Note: language model implementations may internally
 * use a context-encoding without implementing this interface).
 * 
 * @author adampauls
 * 
 * @param <W>
 */
public interface ContextEncodedNgramLanguageModel<W> extends NgramLanguageModelBase<W>
{

	/**
	 * Simple class for returning context offsets
	 * 
	 * @author adampauls
	 * 
	 */
	public static class LmContextInfo
	{

		/**
		 * Offset of context (prefix) of an n-gram
		 */
		public long offset = -1L;

		/**
		 * The (0-based) length of <code>context</code> (i.e.
		 * <code>order == 0</code> iff <code>context</code> refers to a
		 * unigram).
		 * 
		 * Use -1 for an empty context.
		 */
		public int order = -1;

	}

	/**
	 * 
	 * @param contextOffset
	 *            Offset of context (prefix) of an n-gram
	 * @param contextOrder
	 *            The (0-based) length of <code>context</code> (i.e.
	 *            <code>order == 0</code> iff <code>context</code> refers to a
	 *            unigram).
	 * @param word
	 *            Last word of the n-gram
	 * @param outputContext
	 *            Offset of the suffix of the input n-gram. If the parameter is
	 *            <code>null</code> it will be ignored.
	 */
	public float getLogProb(long contextOffset, int contextOrder, int word, @OutputParameter LmContextInfo outputContext);

	/**
	 * 
	 * Convenience method -- the list is first converted to a context-encoding.
	 * This is inefficient, and user code should directly provide context
	 * encodings if speed is important.
	 * 
	 * @see #getLogProb(long, int, int,LmContextInfo)
	 * @param ngram
	 * @param outputContext
	 *            If the parameter is <code>null</code> it will be ignored.
	 */
	public float getLogProb(List<W> ngram, @OutputParameter LmContextInfo outputContext);

	/**
	 * Gets the offset which refers to an n-gram. If the n-gram is not in the
	 * model, then it returns the shortest suffix of the n-gram which is. This
	 * operation is not necessarily fast.
	 * 
	 * @param ngram
	 */
	public LmContextInfo getOffsetForNgram(int[] ngram, int startPos, int endPos);

	public static class DefaultImplementations
	{

		public static <T> float scoreSentence(final List<T> sentence, final ContextEncodedNgramLanguageModel<T> lm) {
			final List<T> sentenceWithBounds = new BoundedList<T>(sentence, lm.getWordIndexer().getStartSymbol(), lm.getWordIndexer().getEndSymbol());

			final int lmOrder = lm.getLmOrder();
			float sentenceScore = 0.0f;
			for (int i = 1; i < lmOrder - 1 && i <= sentenceWithBounds.size() + 1; ++i) {
				final List<T> ngram = sentenceWithBounds.subList(-1, i);
				final float scoreNgram = lm.getLogProb(ngram, null);
				sentenceScore += scoreNgram;
			}
			for (int i = lmOrder - 1; i < sentenceWithBounds.size() + 2; ++i) {
				final List<T> ngram = sentenceWithBounds.subList(i - lmOrder, i);
				final float scoreNgram = lm.getLogProb(ngram, null);
				sentenceScore += scoreNgram;
			}
			return sentenceScore;
		}

		public static <T> float getLogProb(final List<T> ngram, final LmContextInfo contextOutput_, final ContextEncodedNgramLanguageModel<T> lm) {
			LmContextInfo contextOutput = contextOutput_ == null ? null : new LmContextInfo();
			contextOutput.offset = 0;
			contextOutput.order = -1;
			final WordIndexer<T> wordIndexer = lm.getWordIndexer();
			for (int i = 0; i < ngram.size(); ++i) {
				final float score = lm.getLogProb(contextOutput.offset, contextOutput.order, wordIndexer.getOrAddIndex(ngram.get(i)), contextOutput);
				if (i == ngram.size() - 1) return score;
			}
			throw new RuntimeException("Should not get here");

		}

	}

}
