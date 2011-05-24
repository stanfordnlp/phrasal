package edu.berkeley.nlp.lm.bits;

import java.util.BitSet;

public final class BitList
{
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + numBits;
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		final BitList other = (BitList) obj;
		if (data == null) {
			if (other.data != null) return false;
		} else if (!data.equals(other.data)) return false;
		if (numBits != other.numBits) return false;
		return true;
	}

	final BitSet data;

	int numBits;

	public BitList() {
		data = new BitSet();
		numBits = 0;
	}

	public void or(final BitList bitList) {
		for (int i = 0; i < bitList.numBits; ++i) {
			set(i, bitList.get(i));
		}
	}

	public void add(final boolean b) {
		set(numBits, b);
	}

	public boolean get(final int i) {
		if (i >= size()) throw new IndexOutOfBoundsException();
		return data.get(i);
	}

	public int size() {
		return numBits;
	}

	public void set(final int index, final boolean b) {
		data.set(index, b);
		numBits = Math.max(numBits, index + 1);
	}

	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder("");
		for (int i = 0; i < numBits; ++i)
			s.append(data.get(i) ? "1" : "0");
		return s.toString();
	}

	public void addAll(final BitList bits) {
		for (int i = 0; i < bits.size(); ++i)
			add(bits.get(i));
	}

	public void addLong(final long l) {
		final int size = Long.SIZE;
		addHelp(l, size);
	}

	/**
	 * @param l
	 * @param size
	 */
	private void addHelp(final long l, final int size) {
		for (int b = size - 1; b >= 0; --b) {
			add((l & (1L << b)) != 0);
		}
	}

	public void addShort(final short l) {
		addHelp(l, Short.SIZE);
	}

	public void addByte(final byte l) {
		addHelp(l, Byte.SIZE);
	}

	public void clear() {
		numBits = 0;
	}
}