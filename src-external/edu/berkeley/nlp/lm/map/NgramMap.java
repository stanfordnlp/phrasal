package edu.berkeley.nlp.lm.map;

import java.util.List;

import edu.berkeley.nlp.lm.ContextEncodedNgramLanguageModel.LmContextInfo;
import edu.berkeley.nlp.lm.values.ValueContainer;

public interface NgramMap<T>
{

	public long put(int[] ngram, T val);

	public void handleNgramsFinished(int justFinishedOrder);

	public void trim();

	public void initWithLengths(List<Long> numNGrams);

	public T getValue(int[] ngram, int startPos, int endPos, LmContextInfo contextOutput);

	public ValueContainer<T> getValues();

	public LmContextInfo getOffsetForNgram(int[] ngram, int startPos, int endPos);

}
