package edu.berkeley.nlp.lm;

import java.io.Serializable;
import java.util.List;

/**
 * Default implementation of all NGramLanguageModel functionality except the
 * getLogProb(int[] ngram, int startPos, int endPos) function.
 * 
 * @author adampauls
 * 
 * @param <W>
 */
public abstract class AbstractNgramLanguageModel<W> implements NgramLanguageModel<W>, Serializable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected final int lmOrder;

	protected final WordIndexer<W> wordIndexer;

	public AbstractNgramLanguageModel(final int lmOrder, final WordIndexer<W> wordIndexer) {
		this.lmOrder = lmOrder;
		this.wordIndexer = wordIndexer;
	}

	@Override
	public int getLmOrder() {
		return lmOrder;
	}

	@Override
	public float scoreSequence(final List<W> sequence) {
		return NgramLanguageModel.DefaultImplementations.scoreSequence(sequence, this);
	}

	@Override
	public float scoreSentence(final List<W> sentence) {
		return NgramLanguageModel.DefaultImplementations.scoreSentence(sentence, this);
	}

	@Override
	public float getLogProb(final List<W> phrase) {
		return NgramLanguageModel.DefaultImplementations.getLogProb(phrase, this);
	}

	@Override
	public float getLogProb(final int[] ngram) {
		return NgramLanguageModel.DefaultImplementations.getLogProb(ngram, this);
	}

	@Override
	public WordIndexer<W> getWordIndexer() {
		return wordIndexer;
	}

	@Override
	public abstract float getLogProb(final int[] ngram, int startPos, int endPos);

}
