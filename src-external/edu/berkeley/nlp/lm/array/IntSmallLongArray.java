package edu.berkeley.nlp.lm.array;

import java.io.Serializable;
import java.util.Arrays;

public final class IntSmallLongArray implements Serializable, LongArray
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -9133624434714616987L;

	private long size;

	private int[] data;

	public IntSmallLongArray(final long initialCapacity) {
		this.size = 0;
		allocFor(initialCapacity, null);
	}

	/**
	 * @param capacity
	 */
	private void allocFor(final long capacity, final int[] old) {
		check(capacity);
		final int numInner = i(capacity);
		this.data = old == null ? new int[numInner] : Arrays.copyOf(old, numInner);
	}

	/**
	 * @param capacity
	 */
	private void check(final long capacity) {
		if (capacity >= Integer.MAX_VALUE) throw new IllegalArgumentException(capacity + " to big for " + IntSmallLongArray.class.getSimpleName());
	}

	private static final int i(final long l) {
		return (int) l;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#set(long, long)
	 */
	@Override
	public void set(final long pos, final long val) {
		if (pos >= size) throw new ArrayIndexOutOfBoundsException("" + pos);
		setHelp(pos, val);

	}

	/**
	 * @param pos
	 * @param val
	 */
	private void setHelp(final long pos, final long val) {
		data[i(pos)] = (int) val;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.berkeley.nlp.mt.lm.util.collections.LongArray#setAndGrowIfNeeeded
	 * (long, long)
	 */
	@Override
	public void setAndGrowIfNeeded(final long pos, final long val) {
		check(pos);
		ensureCapacity(pos + 1);
		size = Math.max(size, pos + 1);
		setHelp(pos, val);
	}

	public void ensureCapacity(final long minCapacity) {
		final int oldCapacity = sizeOf(data);
		if (minCapacity > oldCapacity) {
			final int[] oldData = data;
			int newCapacity = Math.min(Integer.MAX_VALUE, (oldCapacity * 3) / 2 + 1);
			if (newCapacity < minCapacity) newCapacity = (int) minCapacity;

			allocFor(newCapacity, oldData);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#get(long)
	 */
	@Override
	public long get(final long pos) {
		if (pos >= size) throw new ArrayIndexOutOfBoundsException("" + pos);
		return getHelp(pos);
	}

	private static int sizeOf(final int[] a) {
		return a.length;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#trim()
	 */
	@Override
	public void trim() {
		allocFor(size, data);
	}

	/**
	 * @param pos
	 */
	private long getHelp(final long pos) {
		return data[i(pos)];
	}

	public static void main(final String[] argv) {

		final LongArray b = new IntSmallLongArray(5L + Integer.MAX_VALUE / 9);
		final long val = 10000000000000L;
		b.set(4L + Integer.MAX_VALUE / 9, val);
		final long z = b.get(4L + Integer.MAX_VALUE / 9);
		assert z == val;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#size()
	 */
	@Override
	public long size() {
		return size;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#add(long)
	 */
	@Override
	public boolean add(final long val) {
		setAndGrowIfNeeded(size, val);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#trimToSize(long)
	 */
	@Override
	public void trimToSize(final long size) {
		allocFor(size, data);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.mt.lm.util.collections.LongArray#fill(long, long)
	 */
	@Override
	public void fill(final long l, final long initialCapacity) {
		for (int i = (int) initialCapacity; i >= 0; --i)
			setAndGrowIfNeeded(i, l);
	}

}
