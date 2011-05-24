package edu.berkeley.nlp.lm.collections;

import java.io.Serializable;

/**
 * Contains some limited shared functionality between Custom[type]Maps
 * 
 * @author Adam Pauls
 * @author Percy Liang
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractTMap<T extends Comparable> implements Serializable
{
	protected static final long serialVersionUID = 42;

	public static class Functionality<T extends Comparable> implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@SuppressWarnings("unchecked")
		public T[] createArray(final int n) {
			return (T[]) (new Comparable[n]);
		}

		public T intern(final T x) {
			return x;
		} // Override to get desired behavior, e.g., interning
	}

	public static <T extends Comparable> Functionality<T> defaultFunctionality() {
		return new Functionality<T>();
	}

	protected static final int growFactor = 2; // How much extra space (times size) to give for the capacity

	protected static final int defaultExpectedSize = 2;

	protected static final double loadFactor = 0.75; // For hash table

	protected enum MapType
	{
		SORTED_LIST, HASH_TABLE
	}

	protected MapType mapType;

	protected boolean locked; // Are the keys locked

	protected int num;

	protected T[] keys;

	protected Functionality<T> keyFunc;

	protected int numCollisions; // For debugging
}
