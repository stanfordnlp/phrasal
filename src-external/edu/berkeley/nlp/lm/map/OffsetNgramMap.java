package edu.berkeley.nlp.lm.map;

public interface OffsetNgramMap<T> extends NgramMap<T>
{

	public long getOffset(int[] ngram, int startPos, int endPos);

}
