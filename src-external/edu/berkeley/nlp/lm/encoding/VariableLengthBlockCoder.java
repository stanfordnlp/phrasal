package edu.berkeley.nlp.lm.encoding;

import edu.berkeley.nlp.lm.bits.BitList;
import edu.berkeley.nlp.lm.bits.BitStream;

public class VariableLengthBlockCoder implements BitCompressor
{
	private final int radix;

	public VariableLengthBlockCoder(final int radix) {
		this.radix = radix;
	}

	@Override
	public BitList compress(final long n) {
		return CompressionUtils.variableCompress(n, radix);
	}

	@Override
	public long decompress(final BitStream bits) {
		return CompressionUtils.variableDecompress(bits, radix);
	}

}
