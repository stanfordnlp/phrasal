package edu.berkeley.nlp.lm;

import java.util.List;

import edu.berkeley.nlp.lm.collections.BoundedList;

/**
 * 
 * @author adampauls Top-level interface for an n-gram language model.
 * 
 * @param <W>
 *            A type representing words in the language. Can be a
 *            <code>String</code>, or something more complex if needed
 */
public interface NgramLanguageModel<W> extends NgramLanguageModelBase<W>
{

	/**
	 * Calculate language model score of an n-gram.
	 * 
	 * @param ngram
	 *            array of words in integer representation
	 * @param startPos
	 *            start of the portion of the array to be read
	 * @param endPos
	 *            end of the portion of the array to be read.
	 * @return
	 */
	public float getLogProb(int[] ngram, int startPos, int endPos);

	/**
	 * Equivalent to <code>getLogProb(ngram, 0, ngram.length)</code>
	 * 
	 * @see #getLogProb(int[], int, int)
	 */
	public float getLogProb(int[] ngram);

	/**
	 * Convenience method -- the list is first converted to an int[]
	 * representation. This is general inefficient, and user code should
	 * directly provide int[] arrays.
	 * 
	 * @see #getLogProb(int[], int, int)
	 * @param ngram
	 * @return
	 */
	public float getLogProb(List<W> ngram);

	/**
	 * Scores sequence possibly containing multiple n-grams, but not a complete
	 * sentence.
	 * 
	 * @return
	 */
	public float scoreSequence(List<W> sequence);

	public static class DefaultImplementations
	{

		public static <T> float scoreSentence(final List<T> sentence, final NgramLanguageModel<T> lm) {
			final List<T> sentenceWithBounds = new BoundedList<T>(sentence, lm.getWordIndexer().getStartSymbol(), lm.getWordIndexer().getEndSymbol());

			final int lmOrder = lm.getLmOrder();
			float sentenceScore = 0.0f;
			for (int i = 1; i < lmOrder - 1 && i <= sentenceWithBounds.size() + 1; ++i) {
				final List<T> ngram = sentenceWithBounds.subList(-1, i);
				final float scoreNgram = lm.getLogProb(ngram);
				sentenceScore += scoreNgram;
			}
			for (int i = lmOrder - 1; i < sentenceWithBounds.size() + 2; ++i) {
				final List<T> ngram = sentenceWithBounds.subList(i - lmOrder, i);
				final float scoreNgram = lm.getLogProb(ngram);
				sentenceScore += scoreNgram;
			}
			return sentenceScore;
		}

		public static <T> float getLogProb(final int[] ngram, final NgramLanguageModel<T> lm) {
			return lm.getLogProb(ngram, 0, ngram.length);
		}

		public static <T> float scoreSequence(final List<T> sequence, final NgramLanguageModel<T> lm) {
			float sentenceScore = 0.0f;

			final int lmOrder = lm.getLmOrder();
			for (int i = 0; i + lmOrder - 1 < sequence.size(); ++i) {
				final List<T> ngram = sequence.subList(i, i + lmOrder);
				final float scoreNgram = lm.getLogProb(ngram);
				sentenceScore += scoreNgram;
			}
			return sentenceScore;
		}

		public static <T> float getLogProb(final List<T> ngram, final NgramLanguageModel<T> lm) {
			final int[] ints = new int[ngram.size()];
			final WordIndexer<T> wordIndexer = lm.getWordIndexer();
			for (int i = 0; i < ngram.size(); ++i) {
				ints[i] = wordIndexer.getOrAddIndex(ngram.get(i));
			}
			return lm.getLogProb(ints, 0, ints.length);

		}

	}

}
