package edu.berkeley.nlp.lm.util.hash;

import java.io.Serializable;

public interface HashFunction extends Serializable
{

	public long hash(int[] data, int startPos, int endPos, int prime);

}
