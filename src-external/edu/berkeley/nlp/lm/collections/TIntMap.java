package edu.berkeley.nlp.lm.collections;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Provides a map from objects to non-negative integers. Motivation: provides a
 * specialized data structure for mapping objects to doubles which is both fast
 * and space efficient. Feature 1: You can switch between two representations of
 * the map: - Sorted list (lookups involve binary search) - Hash table with
 * linear probing (lookups involve hashing) Feature 2: Sometimes, we want
 * several maps with the same set of keys. If we lock the map, we can share the
 * same keys between several maps, which saves space.
 * 
 * Note: in the sorted list, we first sort the keys by hash code, and then for
 * equal hash code, we sort by the objects values. We hope that hash code
 * collisions will be rare enough that we won't have to resort to comparing
 * objects.
 * 
 * Typical usage: - Construct a map using a hash table. - To save space, switch
 * to a sorted list representation.
 * 
 * Will get runtime exception if try to used sorted list and keys are not
 * comparable.
 * 
 * @author Adam Pauls
 * @author Percy Liang
 */
@SuppressWarnings({ "ucd", "rawtypes" })
public class TIntMap<T extends Comparable> extends AbstractTMap<T> implements Iterable<TIntMap<T>.Entry>, Serializable
{
	protected static final long serialVersionUID = 42;

	public TIntMap() {
		this(AbstractTMap.<T> defaultFunctionality(), defaultExpectedSize);
	}

	public TIntMap(final Functionality<T> keyFunc) {
		this(keyFunc, defaultExpectedSize);
	}

	public TIntMap(final int expectedSize) {
		this(AbstractTMap.<T> defaultFunctionality(), expectedSize);
	}

	// If keys are locked, we can share the same keys.
	public TIntMap(final AbstractTMap<T> map) {
		this(map.keyFunc);
		this.mapType = map.mapType;
		this.locked = map.locked;
		this.num = map.num;
		this.keys = map.locked ? map.keys : (T[]) map.keys.clone(); // Share keys! CHECKED
		if (map instanceof TIntMap)
			this.values = ((TIntMap<T>) map).values.clone();
		else
			this.values = new int[keys.length];
	}

	/**
	 * expectedSize: expected number of entries we're going to have in the map.
	 */
	public TIntMap(final Functionality<T> keyFunc, final int expectedSize) {
		this.keyFunc = keyFunc;
		this.mapType = MapType.HASH_TABLE;
		this.locked = false;
		this.num = 0;
		allocate(getCapacity(expectedSize, false));
		this.numCollisions = 0;
	}

	// Main operations
	public boolean containsKey(final T key) {
		return find(key, false) != -1;
	}

	public int get(final T key, final int defaultValue) {
		final int i = find(key, false);
		return i == -1 ? defaultValue : values[i];
	}

	public int getSure(final T key) {
		// Throw exception if key doesn't exist.
		final int i = find(key, false);
		if (i == -1) throw new RuntimeException("Missing key: " + key);
		return values[i];
	}

	public void put(final T key, final int value) {
		assert !Double.isNaN(value);
		final int i = find(key, true);
		keys[i] = key;
		values[i] = value;
	}

	public void put(final T key, final int value, final boolean keepHigher) {
		assert !Double.isNaN(value);
		final int i = find(key, true);
		keys[i] = key;
		if (keepHigher && values[i] > value) return;
		values[i] = value;
	}

	public void incr(final T key, final int dValue) {
		final int i = find(key, true);
		keys[i] = key;
		if (Double.isNaN(values[i]))
			values[i] = dValue; // New value
		else
			values[i] += dValue;
	}

	public void incrIfKeyExists(final T key, final int dValue) {
		final int i = find(key, false);
		if (i == -1) return;
		keys[i] = key;
		if (Double.isNaN(values[i]))
			values[i] = dValue; // New value
		else
			values[i] += dValue;
	}

	public void scale(final T key, final int dValue) {
		final int i = find(key, true);
		if (i == -1) return;
		values[i] *= dValue;
	}

	public int size() {
		return num;
	}

	public int capacity() {
		return keys.length;
	}

	/*
	 * public void clear() { // Keep the same capacity num = 0; for(int i = 0; i
	 * < keys.length; i++) keys[i] = null; }
	 */
	public void gut() {
		values = null;
	} // Save memory

	// Simple operations on values
	// Implement them here for maximum efficiency.
	public double sum() {
		double sum = 0;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null) sum += values[i];
		return sum;
	}

	public void putAll(final int value) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null) values[i] = value;
	}

	public void incrAll(final int dValue) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null) values[i] += dValue;
	}

	public void multAll(final int dValue) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null) values[i] *= dValue;
	}

	// Return the key with the maximum value
	public T argmax() {
		int besti = -1;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null && (besti == -1 || values[i] > values[besti])) besti = i;
		return besti == -1 ? null : keys[besti];
	}

	// Return the maximum value
	public double max() {
		int besti = -1;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null && (besti == -1 || values[i] > values[besti])) besti = i;
		return besti == -1 ? Double.NEGATIVE_INFINITY : values[besti];
	}

	// For each (key, value) in map, increment this's key by factor*value
	public void incrMap(final TIntMap<T> map, final int factor) {
		for (int i = 0; i < map.keys.length; i++)
			if (map.keys[i] != null) incr(map.keys[i], factor * map.values[i]);
	}

	// If keys are locked, we can share the same keys.
	public TIntMap<T> copy() {
		final TIntMap<T> newMap = new TIntMap<T>(keyFunc);
		newMap.mapType = mapType;
		newMap.locked = locked;
		newMap.num = num;
		newMap.keys = locked ? keys : (T[]) keys.clone(); // Share keys! CHECKED
		newMap.values = values.clone();
		return newMap;
	}

	// Return a map with only keys in the set
	public TIntMap<T> restrict(final Set<T> set) {
		final TIntMap<T> newMap = new TIntMap<T>(keyFunc);
		newMap.mapType = mapType;
		if (mapType == MapType.SORTED_LIST) {
			allocate(getCapacity(num, false));
			for (int i = 0; i < keys.length; i++) {
				if (set.contains(keys[i])) {
					newMap.keys[newMap.num] = keys[i];
					newMap.values[newMap.num] = values[i];
					newMap.num++;
				}
			}
		} else if (mapType == MapType.HASH_TABLE) {
			for (int i = 0; i < keys.length; i++)
				if (keys[i] != null && set.contains(keys[i])) newMap.put(keys[i], values[i]);
		}
		newMap.locked = locked;
		return newMap;
	}

	// For sorting the entries.
	// Warning: this class has the overhead of the parent class
	private class FullEntry implements Comparable<FullEntry>
	{
		private FullEntry(final T key, final int value) {
			this.key = key;
			this.value = value;
		}

		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public int compareTo(final FullEntry e) {
			final int h1 = hash(key);
			final int h2 = hash(e.key);
			if (h1 != h2) return h1 - h2;
			return ((Comparable) key).compareTo(e.key);
		}

		@Override
		public boolean equals(final Object o) {
			throw new UnsupportedOperationException();
		}

		private final T key;

		private final int value;
	}

	// Compare by value.
	public class EntryValueComparator implements Comparator<Entry>
	{
		@Override
		public int compare(final Entry e1, final Entry e2) {
			return Double.compare(values[e1.i], values[e2.i]);
		}
	}

	public EntryValueComparator entryValueComparator() {
		return new EntryValueComparator();
	}

	// For iterating.
	public class Entry
	{
		private Entry(final int i) {
			this.i = i;
		}

		public T getKey() {
			return keys[i];
		}

		public int getValue() {
			return values[i];
		}

		public void setValue(final int newValue) {
			values[i] = newValue;
		}

		private final int i;
	}

	public void lock() {
		locked = true;
	}

	public void switchToSortedList() {
		switchMapType(MapType.SORTED_LIST);
	}

	public void switchToHashTable() {
		switchMapType(MapType.HASH_TABLE);
	}

	//////////////////////////////////////////////////////////// 

	public class EntrySet extends AbstractSet<Entry>
	{
		@Override
		public Iterator<Entry> iterator() {
			return new EntryIterator();
		}

		@Override
		public int size() {
			return num;
		}

		@Override
		public boolean contains(final Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(final Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	public class KeySet extends AbstractSet<T>
	{
		@Override
		public Iterator<T> iterator() {
			return new KeyIterator();
		}

		@Override
		public int size() {
			return num;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean contains(final Object o) {
			return containsKey((T) o);
		} // CHECKED

		@Override
		public boolean remove(final Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	public class ValueCollection extends AbstractCollection<Integer>
	{
		@Override
		public Iterator<Integer> iterator() {
			return new ValueIterator();
		}

		@Override
		public int size() {
			return num;
		}

		@Override
		public boolean contains(final Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public EntryIterator iterator() {
		return new EntryIterator();
	}

	public EntrySet entrySet() {
		return new EntrySet();
	}

	public KeySet keySet() {
		return new KeySet();
	}

	public ValueCollection values() {
		return new ValueCollection();
	}

	// WARNING: no checks that this iterator is only used when
	// the map is not being structurally changed
	private class EntryIterator extends MapIterator<Entry>
	{
		@Override
		public Entry next() {
			return new Entry(nextIndex());
		}
	}

	private class KeyIterator extends MapIterator<T>
	{
		@Override
		public T next() {
			return keys[nextIndex()];
		}
	}

	private class ValueIterator extends MapIterator<Integer>
	{
		@Override
		public Integer next() {
			return values[nextIndex()];
		}
	}

	private abstract class MapIterator<E> implements Iterator<E>
	{
		public MapIterator() {
			if (mapType == MapType.SORTED_LIST)
				end = size();
			else
				end = capacity();
			next = -1;
			nextIndex();
		}

		@Override
		public boolean hasNext() {
			return next < end;
		}

		int nextIndex() {
			final int curr = next;
			do {
				next++;
			} while (next < end && keys[next] == null);
			return curr;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		private int next, end;
	}

	//////////////////////////////////////////////////////////// 

	/**
	 * How much capacity do we need for this type of map, given that we want n
	 * elements. compact: whether we want to save space and don't plan on
	 * growing.
	 */
	private int getCapacity(final int n, final boolean compact) {
		int capacity;
		if (mapType == MapType.SORTED_LIST)
			capacity = compact ? n : n * growFactor;
		else if (mapType == MapType.HASH_TABLE) {
			capacity = n * growFactor + 2; // Make sure there's enough room for n+2 more entries
		} else
			throw new RuntimeException("Internal bug");
		return Math.max(capacity, 1);
	}

	/**
	 * Convert the map to the given type.
	 */
	private synchronized void switchMapType(final MapType newMapType) {
		assert !locked;

		//System.out.println("switchMapType(" + newMapType + ", " + compact + ")");

		// Save old keys and values, allocate space
		final T[] oldKeys = keys;
		final int[] oldValues = values;
		mapType = newMapType;
		allocate(getCapacity(num, true));
		numCollisions = 0;

		if (newMapType == MapType.SORTED_LIST) {
			// Sort the keys
			final List<FullEntry> entries = new ArrayList<FullEntry>(num);
			for (int i = 0; i < oldKeys.length; i++)
				if (oldKeys[i] != null) entries.add(new FullEntry(oldKeys[i], oldValues[i]));
			Collections.sort(entries);

			// Populate the sorted list
			for (int i = 0; i < num; i++) {
				keys[i] = entries.get(i).key;
				values[i] = entries.get(i).value;
			}
		} else if (mapType == MapType.HASH_TABLE) {
			// Populate the hash table
			num = 0;
			for (int i = 0; i < oldKeys.length; i++) {
				if (oldKeys[i] != null) put(oldKeys[i], oldValues[i]);
			}
		}
	}

	/**
	 * Return the first index i for which the target key is less than or equal
	 * to key i (00001111). Should insert target key at position i. If target is
	 * larger than all of the elements, return size().
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private int binarySearch(final T targetKey) {
		final int targetHash = hash(targetKey);
		int l = 0, u = num;
		while (l < u) {
			//System.out.println(l);
			final int m = (l + u) >>> 1;
			final int keyHash = hash(keys[m]);
			if (targetHash < keyHash || (targetHash == keyHash && ((Comparable) targetKey).compareTo(keys[m]) <= 0))
				u = m;
			else
				l = m + 1;
		}
		return l;
	}

	// Modified hash (taken from HashMap.java).
	private int hash(final T x) {
		int h = x.hashCode();
		h += ~(h << 9);
		h ^= (h >>> 14);
		h += (h << 4);
		h ^= (h >>> 10);
		if (h < 0) h = -h; // New
		return h;
	}

	/**
	 * Modify is whether to make room for the new key if it doesn't exist. If a
	 * new entry is created, the value at that position will be Double.NaN.
	 * Here's where all the magic happens.
	 */
	private synchronized int find(final T key, final boolean modify) {
		//System.out.println("find " + key + " " + modify + " " + mapType + " " + capacity());
		if (mapType == MapType.SORTED_LIST) {
			// Binary search
			final int i = binarySearch(key);
			if (i < num && keys[i] != null && key.equals(keys[i])) return i;
			if (modify) {
				if (locked) throw new RuntimeException("Cannot make new entry for " + key + ", because map is locked");

				if (num == capacity()) changeSortedListCapacity(getCapacity(num + 1, false));

				// Shift everything forward
				for (int j = num; j > i; j--) {
					keys[j] = keys[j - 1];
					values[j] = values[j - 1];
				}
				num++;
				values[i] = -1;
				return i;

			} else
				return -1;
		} else if (mapType == MapType.HASH_TABLE) {
			final int capacity = capacity();
			final int keyHash = hash(key);
			int i = keyHash % capacity;
			if (i < 0) i = -i; // Arbitrary transformation

			// Make sure big enough
			if (!locked && modify && (num > loadFactor * capacity || capacity <= num + 1)) {
				/*
				 * if(locked) throw new
				 * RuntimeException("Cannot make new entry for " + key +
				 * ", because map is locked");
				 */

				switchMapType(MapType.HASH_TABLE);
				return find(key, modify);
			}

			//System.out.println("!!! " + keyHash + " " + capacity);
			if (num == capacity) throw new RuntimeException("Hash table is full: " + capacity);
			while (keys[i] != null && !keys[i].equals(key)) { // Collision
				// Warning: infinite loop if the hash table is full
				// (but this shouldn't happen based on the check above)
				i++;
				numCollisions++;
				if (i == capacity) i = 0;
			}
			if (keys[i] != null) { // Found
				assert key.equals(keys[i]);
				return i;
			}
			if (modify) { // Not found
				num++;
				values[i] = -1;
				return i;
			} else
				return -1;
		} else
			throw new RuntimeException("Internal bug: " + mapType);

	}

	private void allocate(final int n) {
		keys = keyFunc.createArray(n);
		values = new int[n];
	}

	// Resize the sorted list to the new capacity.
	private void changeSortedListCapacity(final int newCapacity) {
		assert mapType == MapType.SORTED_LIST;
		assert newCapacity >= num;
		final T[] oldKeys = keys;
		final int[] oldValues = values;
		allocate(newCapacity);
		System.arraycopy(oldKeys, 0, keys, 0, num);
		System.arraycopy(oldValues, 0, values, 0, num);
	}

	/**
	 * Format: mapType, num, (key, value) pairs
	 */
	private void writeObject(final ObjectOutputStream out) throws IOException {
		out.writeObject(mapType);
		out.writeInt(num);
		for (final Entry e : this) {
			out.writeObject(e.getKey());
			out.writeDouble(e.getValue());
		}
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		this.mapType = (MapType) in.readObject();
		this.num = 0;
		this.locked = false;

		final int n = in.readInt();
		allocate(getCapacity(n, true));

		for (int i = 0; i < n; i++) {
			@SuppressWarnings("unchecked")
			final T key = keyFunc.intern((T) in.readObject());
			final int value = in.readInt();
			if (mapType == MapType.SORTED_LIST) {
				// Assume keys and values serialized in sorted order
				keys[num] = key;
				values[num] = value;
				num++;
			} else if (mapType == MapType.HASH_TABLE) {
				put(key, value);
			}
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (final TIntMap<T>.Entry entry : entrySet()) {
			sb.append(entry.getKey() + ":" + entry.getValue() + ", ");
		}
		sb.append("]");
		return sb.toString();
	}

	private int[] values;
}
