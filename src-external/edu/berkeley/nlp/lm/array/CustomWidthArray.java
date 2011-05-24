package edu.berkeley.nlp.lm.array;

import java.io.Serializable;
import java.util.Arrays;

/**
 * An array with a custom word "width" in bits. Only handles arrays with 2^37
 * bits.
 * 
 * @author adampauls
 * 
 */
@SuppressWarnings("ucd")
public final class CustomWidthArray implements LongArray, Serializable
{
	private static final long ALL_ONES = 0xFFFFFFFFFFFFFFFFL;

	private static final long serialVersionUID = 1L;

	private final static int LOG2_BITS_PER_WORD = 6;

	private final static int BITS_PER_WORD = 1 << LOG2_BITS_PER_WORD;

	private final static int WORD_MASK = BITS_PER_WORD - 1;

	private long size;

	private final int width;

	private final long fullMask;

	private long[] data;

	private final static int numLongs(final long size) {
		assert (size + WORD_MASK) >>> LOG2_BITS_PER_WORD <= Integer.MAX_VALUE;
		return (int) ((size + WORD_MASK) >>> LOG2_BITS_PER_WORD);
	}

	private final static int word(final long index) {
		assert index >>> LOG2_BITS_PER_WORD <= Integer.MAX_VALUE;
		return (int) (index >>> LOG2_BITS_PER_WORD);
	}

	private final static int bit(final long index) {
		return (int) (index & WORD_MASK);
	}

	private final static long mask(final long index) {
		return 1L << (index & WORD_MASK);
	}

	public CustomWidthArray(final long numWords, final int width) {
		final long numBits = numWords * width;
		assert (numBits <= (Integer.MAX_VALUE + 1L) * Long.SIZE) : ("CustomWidthArray can only be 2^37 bits long");
		data = new long[numLongs(numBits)];

		this.width = width;
		size = 0;
		fullMask = width == Long.SIZE ? -1 : (1L << width) - 1;
	}

	private long length() {
		return size;
	}

	public void ensureCapacity(final long numWords) {
		final long numBits = numWords * width;
		assert (numBits <= (Integer.MAX_VALUE + 1L) * Long.SIZE) : ("CustomWidthArray can only be 2^37 bits long");
		if (numWords >= data.length) data = Arrays.copyOf(data, Math.max(numLongs(numBits), data.length * 3 / 2 + 1));

	}

	@Override
	public void trim() {
		trimToSize(size);
	}

	/**
	 * @param sizeHere
	 */
	@Override
	public void trimToSize(final long sizeHere) {
		final long numBits = sizeHere * width;
		//		if (data.length == numLongs(numBits)) return false;
		data = Arrays.copyOf(data, numLongs(numBits));
		//		return true;
	}

	public void clear() {
		Arrays.fill(data, 0, word(size - 1) + 1, 0);
		size = 0;
	}

	private void rangeCheck(final long index) {
		if (index >= length()) throw new IndexOutOfBoundsException("Index (" + index + ") is greater than length (" + (length()) + ")");
	}

	public boolean getBit(final long index) {
		rangeCheck(index);
		return (data[word(index)] & mask(index)) != 0;
	}

	public boolean set(final long index, final boolean value) {
		rangeCheck(index);
		final int word = word(index);
		final long mask = mask(index);
		final boolean oldValue = (data[word] & mask) != 0;
		if (value)
			data[word] |= mask;
		else
			data[word] &= ~mask;
		return oldValue != value;
	}

	public void set(final long index) {
		rangeCheck(index);
		data[word(index)] |= mask(index);
	}

	public void clear(final long index) {
		rangeCheck(index);
		data[word(index)] &= ~mask(index);
	}

	private long getLong(final long from, final long to) {
		final long l = Long.SIZE - (to - from);
		final int startWord = word(from);
		final int startBit = bit(from);
		if (l == Long.SIZE) return 0;
		if (startBit <= l) return data[startWord] << l - startBit >>> l;
		return data[startWord] >>> startBit | data[startWord + 1] << Long.SIZE + l - startBit >>> l;
	}

	@Override
	public boolean add(final long value) {
		assert !(width < Long.SIZE && (value & -1L << width) != 0) : "The specified value (" + value
			+ ") is larger than the maximum value for the given width (" + width + ")";
		final long length = this.size * width;
		final int startWord = word(length);
		final int startBit = bit(length);
		ensureCapacity(this.size + 1);

		if (startBit + width <= Long.SIZE)
			data[startWord] |= value << startBit;
		else {
			data[startWord] |= value << startBit;
			data[startWord + 1] = value >>> BITS_PER_WORD - startBit;
		}

		this.size++;
		return true;
	}

	@Override
	public long get(final long index) {
		final long start = index * width;
		return getLong(start, start + width);
	}

	public static int numBitsNeeded(final long n) {
		if (n == 0) return 1;
		final int num = Long.SIZE - Long.numberOfLeadingZeros(n - 1);
		if (n % 2 == 0) return num + 1;
		return num;
	}

	@Override
	public void set(final long index, final long value) {
		rangeCheck(index);
		if (width == 0) return;
		if (width != Long.SIZE && value > fullMask) throw new IllegalArgumentException("Value too large: " + value);
		final long bits[] = data;
		final long start = index * width;
		final int startWord = word(start);
		final int endWord = word(start + width - 1);
		final int startBit = bit(start);
		final long oldValue;

		if (startWord == endWord) {
			oldValue = bits[startWord] >>> startBit & fullMask;
			bits[startWord] &= ~(fullMask << startBit);
			bits[startWord] |= value << startBit;
			assert value == (bits[startWord] >>> startBit & fullMask);
		} else {
			// Here startBit > 0.
			oldValue = bits[startWord] >>> startBit | bits[endWord] << (BITS_PER_WORD - startBit) & fullMask;
			bits[startWord] &= (1L << startBit) - 1;
			bits[startWord] |= value << startBit;
			bits[endWord] &= -(1L << width - BITS_PER_WORD + startBit);
			bits[endWord] |= value >>> BITS_PER_WORD - startBit;

			assert value == (bits[startWord] >>> startBit | bits[endWord] << (BITS_PER_WORD - startBit) & fullMask);
		}
	}

	@Override
	public void setAndGrowIfNeeded(final long pos, final long value) {
		if (pos >= size) {
			ensureCapacity(pos + 2);
			this.size = pos + 1;
		}
		set(pos, value);
	}

	@Override
	public long size() {
		return length();
	}

	@Override
	public void fill(final long l, final long initialCapacity) {
		final long numBits = initialCapacity * width;
		//		if (data.length == numLongs(numBits)) return false;
		Arrays.fill(data, 0, numLongs(numBits), l);
	}

}
