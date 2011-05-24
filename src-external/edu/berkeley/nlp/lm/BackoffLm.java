package edu.berkeley.nlp.lm;

import java.io.Serializable;
import java.util.List;

import edu.berkeley.nlp.lm.map.ContextEncodedNgramMap;
import edu.berkeley.nlp.lm.map.NgramMap;
import edu.berkeley.nlp.lm.map.ConfigOptions;
import edu.berkeley.nlp.lm.map.OffsetNgramMap;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;
import edu.berkeley.nlp.lm.values.ProbBackoffPair;
import edu.berkeley.nlp.lm.values.ProbBackoffValueContainer;

/**
 * Language model implementation which uses Katz-style backoff computation.
 * 
 * @author adampauls
 * 
 * @param <W>
 */
public class BackoffLm<W> extends AbstractContextEncodedNgramLanguageModel<W> implements NgramLanguageModel<W>, ContextEncodedNgramLanguageModel<W>,
	Serializable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected final NgramMap<ProbBackoffPair> map;

	private final ProbBackoffValueContainer values;

	/**
	 * Fixed constant returned when computing the log probability for an n-gram
	 * whose last word is not in the vocabulary. Note that this is different
	 * from the log prob of the <code>unk</code> tag probability.
	 * 
	 */
	private final float oovWordLogProb;

	public BackoffLm(final int lmOrder, final WordIndexer<W> wordIndexer, final NgramMap<ProbBackoffPair> map, final ConfigOptions opts) {
		super(lmOrder, wordIndexer);
		oovWordLogProb = (float) opts.unknownWordLogProb;
		this.map = map;
		this.values = (ProbBackoffValueContainer) map.getValues();

	}

	@Override
	public float getLogProb(final int[] ngram, final int startPos_, final int endPos_) {
		if (map instanceof OffsetNgramMap<?>) {
			return getLogProbWithOffsets(ngram, startPos_, endPos_);
		} else {
			return getLogProbDirectly(ngram, startPos_, endPos_);
		}
	}

	/**
	 * @param ngram
	 * @param startPos_
	 * @param endPos_
	 * @return
	 */
	private float getLogProbDirectly(final int[] ngram, final int startPos_, final int endPos_) {
		int startPos = startPos_;
		int endPos = endPos_;
		float sum = 0.0f;
		while (true) {
			final ProbBackoffPair pair = map.getValue(ngram, startPos, endPos, null);
			if (pair != null && !Float.isNaN(pair.prob)) {
				return sum + pair.prob;
			} else {
				if (endPos - startPos > 1) {
					final ProbBackoffPair backoffPair = map.getValue(ngram, startPos, endPos - 1, null);
					final float backOff = backoffPair == null ? 0.0f : backoffPair.backoff;
					sum += backOff;
					startPos += 1;
				} else {
					return oovWordLogProb;
				}
			}
		}
	}

	/**
	 * @param ngram
	 * @param startPos_
	 * @param endPos_
	 * @return
	 */
	private float getLogProbWithOffsets(final int[] ngram, final int startPos_, final int endPos_) {
		int startPos = startPos_;
		int endPos = endPos_;
		float sum = 0.0f;
		while (true) {
			final OffsetNgramMap<ProbBackoffPair> localMap = (OffsetNgramMap<ProbBackoffPair>) map;

			final long offset = localMap.getOffset(ngram, startPos, endPos);
			if (offset >= 0) {
				final int ngramOrder = endPos - startPos - 1;
				final float prob = values.getProb(ngramOrder, offset);
				if (!Float.isNaN(prob)) return sum + prob;
			}
			if (endPos - startPos > 1) {
				final long backoffIndex = localMap.getOffset(ngram, startPos, endPos - 1);
				float backOff = backoffIndex < 0 ? 0.0f : values.getBackoff(endPos - startPos - 2, backoffIndex);
				backOff = Float.isNaN(backOff) ? 0.0f : backOff;
				sum += backOff;
				startPos += 1;
			} else {
				return oovWordLogProb;
			}

		}
	}

	@Override
	public float getLogProb(final long contextOffset, final int contextOrder, final int word, @OutputParameter final LmContextInfo outputContext) {
		final ContextEncodedNgramMap<ProbBackoffPair> localMap = (ContextEncodedNgramMap<ProbBackoffPair>) map;
		int currContextOrder = contextOrder;
		long currContextOffset = contextOffset;
		float sum = 0.0f;
		while (true) {
			final long offset = localMap.getOffset(currContextOffset, currContextOrder, word);
			final int ngramOrder = currContextOrder + 1;
			final float prob = offset < 0 ? Float.NaN : values.getProb(ngramOrder, offset);
			if (offset >= 0 && !Float.isNaN(prob)) {
				if (outputContext != null) {
					if (ngramOrder == lmOrder - 1) {
						final long suffixOffset = values.getContextOffset(offset, ngramOrder);
						outputContext.offset = suffixOffset;
						outputContext.order = ngramOrder - 1;
					} else {
						outputContext.offset = offset;
						outputContext.order = ngramOrder;

					}
				}
				assert !Float.isNaN(prob);
				return sum + prob;
			} else if (currContextOrder >= 0) {
				final long backoffIndex = currContextOffset;
				final float backOff = backoffIndex < 0 ? 0.0f : values.getBackoff(currContextOrder, backoffIndex);
				sum += (Float.isNaN(backOff) ? 0.0f : backOff);
				currContextOrder--;
				currContextOffset = currContextOrder < 0 ? 0 : values.getContextOffset(currContextOffset, currContextOrder + 1);
			} else {
				return oovWordLogProb;
			}
		}
	}

	@Override
	public WordIndexer<W> getWordIndexer() {
		return wordIndexer;
	}

	@Override
	public float getLogProb(final int[] ngram) {
		return NgramLanguageModel.DefaultImplementations.getLogProb(ngram, this);
	}

	@Override
	public float getLogProb(final List<W> ngram) {
		return NgramLanguageModel.DefaultImplementations.getLogProb(ngram, this);
	}

	@Override
	public float scoreSequence(final List<W> sequence) {
		return NgramLanguageModel.DefaultImplementations.scoreSequence(sequence, this);
	}

	@Override
	public LmContextInfo getOffsetForNgram(int[] ngram, int startPos, int endPos) {
		return map.getOffsetForNgram(ngram, startPos, endPos);
	}

}
