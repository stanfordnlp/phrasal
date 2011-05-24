package edu.berkeley.nlp.lm.encoding;

import edu.berkeley.nlp.lm.bits.BitList;
import edu.berkeley.nlp.lm.bits.BitStream;

public class CompressionUtils
{

	public static BitList variableCompress(final long n, final int radix) {
		final int numBits = getNumBits(n);
		final int bitsPerDigit = radix - 1;
		final int numDigits = getNumDigits(numBits, bitsPerDigit);
		final BitList bits = writeUnary(numDigits);
		final int numBitsToOutput = numDigits * bitsPerDigit;
		writeNormalBinary(n, bits, numBitsToOutput);
		assert bits.size() < Long.SIZE;

		return bits;
	}

	public static long variableDecompress(final BitStream input, final int radix) {
		final int numBitsInDigit = radix - 1;
		final int numDigits = readUnary(input);
		final long nextBits = input.next(numBitsInDigit * numDigits);
		return nextBits;
	}

	/**
	 * @param input
	 * @return
	 */
	private static int readUnary(final BitStream input) {
		return input.nextConsecutiveZeros();
	}

	/**
	 * @param n
	 * @param bits
	 * @param numBitsToOutput
	 */
	private static void writeNormalBinary(final long n, final BitList bits, final int numBitsToOutput) {
		long mask = 1L << (numBitsToOutput - 1);
		for (int i = 0; i < numBitsToOutput; ++i) {
			bits.add((n & mask) != 0);
			mask >>>= 1;
		}
	}

	/**
	 * @param numDigits
	 * @return
	 */
	private static BitList writeUnary(final int numDigits) {
		final BitList bits = new BitList();
		for (int i = 0; i < numDigits; ++i) {
			bits.add(i == numDigits - 1);
		}
		return bits;
	}

	/**
	 * @param delta
	 * @return
	 */
	private static int getNumBits(final long delta) {
		return Long.SIZE - Long.numberOfLeadingZeros(delta);
	}

	/**
	 * @param numBits
	 * @param bitsPerDigit
	 * @return
	 */
	private static int getNumDigits(final int numBits, final int bitsPerDigit) {
		int numDigits = (numBits / bitsPerDigit) + ((numBits % bitsPerDigit) == 0 ? 0 : 1);
		numDigits = Math.max(1, numDigits);
		return numDigits;
	}

}
