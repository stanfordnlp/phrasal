package edu.berkeley.nlp.lm.array;

public interface LongArray
{

	public abstract void set(long pos, long val);

	public abstract void setAndGrowIfNeeded(long pos, long val);

	public abstract long get(long pos);

	public abstract void trim();

	public abstract long size();

	public abstract boolean add(long val);

	public abstract void trimToSize(long size);

	public abstract void fill(long l, long initialCapacity);

	public static final class StaticMethods
	{
		public static LongArray newLongArray(final long maxKeySize, final long maxNumKeys) {
			return newLongArray(maxKeySize, maxNumKeys, 10);
		}

		public static LongArray newLongArray(final long maxKeySize, final long maxNumKeys, final long initCapacity) {
			if (maxNumKeys < Integer.MAX_VALUE) {
				if (maxKeySize < Integer.MAX_VALUE) {
					return new IntSmallLongArray(initCapacity);
				} else {
					return new SmallLongArray(initCapacity);
				}
			} else {
				return new LargeLongArray(initCapacity);
			}
		}
	}

}