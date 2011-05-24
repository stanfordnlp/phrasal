package edu.berkeley.nlp.lm.map;

public interface ContextEncodedNgramMap<T> extends OffsetNgramMap<T>
{
	public long getOffset(final long contextOffset, final int contextOrder, final int word);

}
