package edu.berkeley.nlp.lm.array;

import java.io.Serializable;
import java.util.Arrays;

public final class LargeLongArray implements Serializable, LongArray
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -9133624434714616987L;

	private long size;

	private long[][] data;

	public LargeLongArray(final long initialCapacity) {
		this.size = 0;
		allocFor(initialCapacity, null);
	}

	/**
	 * @param capacity
	 */
	private void allocFor(final long capacity, final long[][] old) {
		final int numOuter = o(capacity) + 1;
		final int numInner = i(capacity);
		this.data = new long[numOuter][];
		for (int i = 0; i < numOuter; ++i) {
			final int currSize = (i == numOuter - 1) ? numInner : Integer.MAX_VALUE;
			if (old != null && currSize == old[i].length) {
				data[i] = old[i];
			} else {
				if (old != null && i < old.length) {
					data[i] = Arrays.copyOf(old[i], currSize);
				} else {
					data[i] = new long[currSize];
				}
			}
		}
	}

	private static final int o(final long l) {
		return (int) (l >>> Integer.SIZE);
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
		data[o(pos)][i(pos)] = val;
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
		ensureCapacity(pos + 1);
		size = Math.max(size, pos + 1);
		setHelp(pos, val);
	}

	public void ensureCapacity(final long minCapacity) {
		final long oldCapacity = sizeOf(data);
		if (minCapacity > oldCapacity) {
			final long[][] oldData = data;
			long newCapacity = (oldCapacity * 3) / 2 + 1;
			if (newCapacity < minCapacity) newCapacity = minCapacity;

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

	private static long sizeOf(final long[][] a) {
		long ret = 0;
		for (int i = 0; i < a.length; ++i) {
			ret += a[i].length;
		}
		return ret;
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
	 * @return
	 */
	private long getHelp(final long pos) {
		return data[o(pos)][i(pos)];
	}

	public static void main(final String[] argv) {

		final LongArray b = new LargeLongArray(5L + Integer.MAX_VALUE / 9);
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

	public void shift(final long src, final long dest, final int length) {
		if (length == 0) return;
		if (src == dest) return;
		assert dest >= src;

		final int oStart = o(src);
		final int oEnd = o(src + length);
		final int oDestStart = o(dest);
		final int oDestEnd = o(dest + length);
		if (dest + length >= size) {
			setAndGrowIfNeeded(dest + length, 0);
		}
		if (oStart != oEnd || oDestStart != oDestEnd) {
			for (long i = length - 1; i >= 0; --i) {
				set(dest + i, get(src + i));
			}
		} else {
			System.arraycopy(data[o(src)], i(src), data[o(dest)], i(dest), length);
		}
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
		for (int i = 0; i < initialCapacity; ++i)
			setAndGrowIfNeeded(i, l);
	}

}
