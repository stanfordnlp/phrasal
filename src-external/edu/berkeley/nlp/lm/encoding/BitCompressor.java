package edu.berkeley.nlp.lm.encoding;

import edu.berkeley.nlp.lm.bits.BitList;
import edu.berkeley.nlp.lm.bits.BitStream;

public interface BitCompressor
{

	public BitList compress(long n);

	public long decompress(BitStream bits);

}
