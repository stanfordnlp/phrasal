package edu.berkeley.nlp.lm.bits;

/**
 * Wraps a portion of a long[] array with iterator-like functionality over a
 * stream of bits.
 * 
 * @author adampauls
 * 
 */
public final class BitStream
{

	private static final long LOG_LONG_SIZE = Long.SIZE - Long.numberOfLeadingZeros(Long.SIZE - 1);

	private static final long LOG_LONG_MASK = (1L << LOG_LONG_SIZE) - 1;

	private static final long HIGH_BIT_MASK = (1L << (Long.SIZE - 1));

	final long[] data;

	final long start;

	long currLong = -1;

	long currPos = -1;

	int relBit;

	long markedCurrPos = -1;

	int markedRelBit = -1;

	private final int numBits;

	private final int startBit;

	public BitStream(final long[] data, final long start, final int startBit, final int numBits) {
		this.data = data;
		this.start = start;
		this.currPos = 0;
		this.numBits = numBits;
		this.startBit = startBit;
		currLong = data[(int) start] << startBit;
		this.relBit = startBit;
	}

	public boolean nextBit() {
		assert !finished();
		if (relBit == Long.SIZE) advanceToNextLong();
		final boolean ret = (currLong & HIGH_BIT_MASK) != 0;
		relBit++;
		currLong <<= 1;
		if (relBit == Long.SIZE) {
			advanceToNextLong();
		}
		return ret;
	}

	/**
	 * Reads a string of zeros followed by a 1.
	 * 
	 * @return the number of consecutive zeros (plus 1)
	 */
	public int nextConsecutiveZeros() {
		final int numberOfLeadingZerosOnThisWord = Long.numberOfLeadingZeros(currLong);
		final int numLeft = Long.SIZE - relBit;
		if (numberOfLeadingZerosOnThisWord >= numLeft) {
			advanceToNextLong();
			final int numberOfLeadingZerosOnNextWord = Long.numberOfLeadingZeros(currLong);
			advanceWithinCurrLong(numberOfLeadingZerosOnNextWord + 1);
			return numberOfLeadingZerosOnNextWord + 1 + numLeft;
		} else {
			final int headerLength = numberOfLeadingZerosOnThisWord + 1;
			advanceWithinCurrLong(headerLength);
			return headerLength;
		}
	}

	/**
	 * Read and return next n bits.
	 * 
	 * @param n
	 * @return
	 */
	public long next(final int n) {
		assert n <= Long.SIZE;
		assert !finished();
		final int leftOnThisWord = Math.min(n, Long.SIZE - relBit);
		final int onNextWord = n - leftOnThisWord;
		final int thisWordShift = Long.SIZE - leftOnThisWord;
		long ret = currLong >>> thisWordShift;
		if (onNextWord > 0) {
			advanceToNextLong();
			final int nextWordShift = Long.SIZE - onNextWord;
			ret <<= onNextWord;
			ret |= currLong >>> nextWordShift;
			advanceWithinCurrLong(onNextWord);
		} else {
			advanceWithinCurrLong(leftOnThisWord);
		}
		return ret;
	}

	/**
	 * 
	 */
	private void advanceWithinCurrLong(final int n) {
		assert relBit + n <= Long.SIZE;
		relBit += n;
		currLong <<= n;

	}

	private void advanceToNextLong() {
		currPos++;
		relBit = 0;
		currLong = data[(int) (start + currPos)];
	}

	public boolean finished() {
		return numBitsLeft() <= 0;
	}

	public void rewind(final int i) {
		shiftAbsPosition(-i);
	}

	private void shiftAbsPosition(final int i) {
		final long absBit = (currPos << LOG_LONG_SIZE) + relBit;
		final long rewound = absBit + i;
		final int newRelBit = (int) (rewound & LOG_LONG_MASK);
		final long newPos = rewound >>> LOG_LONG_SIZE;
		reset(newRelBit, newPos);
	}

	private void reset(final int newRelBit, final long newPos) {
		relBit = newRelBit;
		currPos = newPos;
		currLong = data[(int) (start + currPos)] << relBit;
	}

	public int numBitsLeft() {
		return (int) (numBits + startBit - ((currPos << LOG_LONG_SIZE) + relBit));
	}

	/**
	 * Advances without returning any bits.
	 * 
	 * @param n
	 */
	public void advance(final int n) {
		shiftAbsPosition(n);
	}

	/**
	 * Sets a mark at the current bit that can be returned to using
	 * rewindToMark.
	 */
	public void mark() {
		assert markedCurrPos < 0 : "Tried to double mark";
		markedCurrPos = currPos;
		markedRelBit = relBit;
	}

	public void rewindToMark() {
		reset(markedRelBit, markedCurrPos);
		markedCurrPos = -1;
		markedRelBit = -1;
	}

}