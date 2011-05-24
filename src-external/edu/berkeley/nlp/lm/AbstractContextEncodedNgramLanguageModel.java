package edu.berkeley.nlp.lm;

import java.io.Serializable;
import java.util.List;

/**
 * 
 * Default implementation of all ContextEncodedNgramLanguageModel functionality
 * except @see #getLogProb(long , int , int , LmStateOutput ) and @see
 * #getNgramForContext(long , int)
 * 
 * 
 * @author adampauls
 * 
 * @param <W>
 */
public abstract class AbstractContextEncodedNgramLanguageModel<W> implements ContextEncodedNgramLanguageModel<W>, Serializable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -1047526780579521903L;

	protected final int lmOrder;

	protected final WordIndexer<W> wordIndexer;

	public AbstractContextEncodedNgramLanguageModel(final int lmOrder, final WordIndexer<W> wordIndexer) {
		this.lmOrder = lmOrder;
		this.wordIndexer = wordIndexer;
	}

	@Override
	public int getLmOrder() {
		return lmOrder;
	}

	@Override
	public float scoreSentence(final List<W> sentence) {
		return ContextEncodedNgramLanguageModel.DefaultImplementations.scoreSentence(sentence, this);
	}

	@Override
	public float getLogProb(final List<W> phrase, final LmContextInfo contextOutput) {
		return ContextEncodedNgramLanguageModel.DefaultImplementations.getLogProb(phrase, contextOutput, this);
	}

}
