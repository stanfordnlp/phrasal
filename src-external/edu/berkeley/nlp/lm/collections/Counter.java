package edu.berkeley.nlp.lm.collections;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * A map from objects to doubles. Includes convenience methods for getting,
 * setting, and incrementing element counts. Objects not in the counter will
 * return a count of zero. The counter is backed by a HashMap .(unless specified
 * otherwise with the MapFactory constructor).
 * 
 * @author lots of people
 */
public class Counter<E> implements Serializable
{
	private static final long serialVersionUID = 1L;

	Map<E, Double> entries;

	boolean dirty = true;

	double cacheTotal = 0.0;

	double defaultCount = 0.0;

	public double getDefaultCount() {
		return defaultCount;
	}

	public void setDefaultCount(final double deflt) {
		this.defaultCount = deflt;
	}

	/**
	 * The elements in the counter.
	 * 
	 * @return set of keys
	 */
	public Set<E> keySet() {
		return entries.keySet();
	}

	public Set<Entry<E, Double>> entrySet() {
		return entries.entrySet();
	}

	/**
	 * The number of entries in the counter (not the total count -- use
	 * totalCount() instead).
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * True if there are no entries in the counter (false does not mean
	 * totalCount > 0)
	 */
	public boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * Returns whether the counter contains the given key. Note that this is the
	 * way to distinguish keys which are in the counter with count zero, and
	 * those which are not in the counter (and will therefore return count zero
	 * from getCount().
	 * 
	 * @param key
	 * @return whether the counter contains the key
	 */
	public boolean containsKey(final E key) {
		return entries.containsKey(key);
	}

	/**
	 * Get the count of the element, or zero if the element is not in the
	 * counter.
	 * 
	 * @param key
	 */
	public double getCount(final E key) {
		final Double value = entries.get(key);
		if (value == null) return defaultCount;
		return value;
	}

	/**
	 * I know, I know, this should be wrapped in a Distribution class, but it's
	 * such a common use...why not. Returns the MLE prob. Assumes all the counts
	 * are >= 0.0 and totalCount > 0.0. If the latter is false, return 0.0 (i.e.
	 * 0/0 == 0)
	 * 
	 * @author Aria
	 * @param key
	 * @return MLE prob of the key
	 */
	public double getProbability(final E key) {
		final double count = getCount(key);
		final double total = totalCount();
		if (total < 0.0) { throw new RuntimeException("Can't call getProbability() with totalCount < 0.0"); }
		return total > 0.0 ? count / total : 0.0;
	}

	/**
	 * Destructively normalize this Counter in place.
	 */
	public void normalize() {
		final double totalCount = totalCount();
		for (final E key : keySet()) {
			setCount(key, getCount(key) / totalCount);
		}
		dirty = true;
	}

	/**
	 * Set the count for the given key, clobbering any previous count.
	 * 
	 * @param key
	 * @param count
	 */
	public void setCount(final E key, final double count) {
		entries.put(key, count);
		dirty = true;
	}

	/**
	 * Set the count for the given key if it is larger than the previous one;
	 * 
	 * @param key
	 * @param count
	 */
	public void put(final E key, final double count, final boolean keepHigher) {
		if (keepHigher && entries.containsKey(key)) {
			final double oldCount = entries.get(key);
			if (count > oldCount) {
				entries.put(key, count);
			}
		} else {
			entries.put(key, count);
		}
		dirty = true;
	}

	/**
	 * Will return a sample from the counter, will throw exception if any of the
	 * counts are < 0.0 or if the totalCount() <= 0.0
	 * 
	 * 
	 * @author aria42
	 */
	public E sample(final Random rand) {
		final double total = totalCount();
		if (total <= 0.0) { throw new RuntimeException(String.format("Attempting to sample() with totalCount() %.3f\n", total)); }
		double sum = 0.0;
		final double r = rand.nextDouble();
		for (final Map.Entry<E, Double> entry : entries.entrySet()) {
			final double count = entry.getValue();
			final double frac = count / total;
			sum += frac;
			if (r < sum) { return entry.getKey(); }
		}
		throw new IllegalStateException("Shoudl've have returned a sample by now....");
	}

	/**
	 * Will return a sample from the counter, will throw exception if any of the
	 * counts are < 0.0 or if the totalCount() <= 0.0
	 * 
	 * 
	 * @author aria42
	 */
	public E sample() {
		return sample(new Random());
	}

	public void removeKey(final E key) {
		setCount(key, 0.0);
		dirty = true;
		removeKeyFromEntries(key);
	}

	/**
	 * @param key
	 */
	protected void removeKeyFromEntries(final E key) {
		entries.remove(key);
	}

	/**
	 * Set's the key's count to the maximum of the current count and val. Always
	 * sets to val if key is not yet present.
	 * 
	 * @param key
	 * @param val
	 */
	public void setMaxCount(final E key, final double val) {
		final Double value = entries.get(key);
		if (value == null || val > value.doubleValue()) {
			setCount(key, val);

			dirty = true;
		}
	}

	/**
	 * Set's the key's count to the minimum of the current count and val. Always
	 * sets to val if key is not yet present.
	 * 
	 * @param key
	 * @param val
	 */
	public void setMinCount(final E key, final double val) {
		final Double value = entries.get(key);
		if (value == null || val < value.doubleValue()) {
			setCount(key, val);

			dirty = true;
		}
	}

	/**
	 * Increment a key's count by the given amount.
	 * 
	 * @param key
	 * @param increment
	 */
	public double incrementCount(final E key, final double increment) {
		final double newVal = getCount(key) + increment;
		setCount(key, newVal);
		dirty = true;
		return newVal;
	}

	/**
	 * Increment each element in a given collection by a given amount.
	 */
	public void incrementAll(final Collection<? extends E> collection, final double count) {
		for (final E key : collection) {
			incrementCount(key, count);
		}
		dirty = true;
	}

	public <T extends E> void incrementAll(final Counter<T> counter) {
		incrementAll(counter, 1.0);
	}

	public <T extends E> void incrementAll(final Counter<T> counter, final double scale) {
		for (final Entry<T, Double> entry : counter.entrySet()) {
			incrementCount(entry.getKey(), scale * entry.getValue());
		}
		dirty = true;
	}

	/**
	 * Finds the total of all counts in the counter. This implementation
	 * iterates through the entire counter every time this method is called.
	 * 
	 * @return the counter's total
	 */
	public double totalCount() {
		if (!dirty) { return cacheTotal; }
		double total = 0.0;
		for (final Map.Entry<E, Double> entry : entries.entrySet()) {
			total += entry.getValue();
		}
		cacheTotal = total;
		dirty = false;
		return total;
	}

	public Collection<Entry<E, Double>> getEntriesSortedByIncreasingCount() {
		final List<Entry<E, Double>> sorted = new ArrayList<Entry<E, Double>>(entrySet());
		Collections.sort(sorted, new EntryValueComparator(false));
		return sorted;
	}

	public Collection<Entry<E, Double>> getEntriesSortedByDecreasingCount() {
		final List<Entry<E, Double>> sorted = new ArrayList<Entry<E, Double>>(entrySet());
		Collections.sort(sorted, new EntryValueComparator(true));
		return sorted;
	}

	/**
	 * Finds the key with maximum count. This is a linear operation, and ties
	 * are broken arbitrarily.
	 * 
	 * @return a key with minumum count
	 */
	public E argMax() {
		double maxCount = Double.NEGATIVE_INFINITY;
		E maxKey = null;
		for (final Map.Entry<E, Double> entry : entries.entrySet()) {
			if (entry.getValue() > maxCount || maxKey == null) {
				maxKey = entry.getKey();
				maxCount = entry.getValue();
			}
		}
		return maxKey;
	}

	public double min() {
		return maxMinHelp(false);
	}

	public double max() {
		return maxMinHelp(true);
	}

	private double maxMinHelp(final boolean max) {
		double maxCount = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

		for (final Map.Entry<E, Double> entry : entries.entrySet()) {
			if ((max && entry.getValue() > maxCount) || (!max && entry.getValue() < maxCount)) {

				maxCount = entry.getValue();
			}
		}
		return maxCount;
	}

	/**
	 * Returns a string representation with the keys ordered by decreasing
	 * counts.
	 * 
	 * @return string representation
	 */
	@Override
	public String toString() {
		return toStringSortedByKeys();
	}

	public String toStringSortedByKeys() {
		final StringBuilder sb = new StringBuilder("[");

		final NumberFormat f = NumberFormat.getInstance();
		f.setMaximumFractionDigits(5);
		int numKeysPrinted = 0;
		for (final E element : new TreeSet<E>(keySet())) {

			sb.append(element.toString());
			sb.append(" : ");
			sb.append(f.format(getCount(element)));
			if (numKeysPrinted < size() - 1) sb.append(", ");
			numKeysPrinted++;
		}
		if (numKeysPrinted < size()) sb.append("...");
		sb.append("]");
		return sb.toString();
	}

	public Counter() {

		entries = new HashMap<E, Double>();
	}

	public Counter(final Counter<? extends E> counter) {
		this();
		incrementAll(counter);
	}

	public Counter(final Collection<? extends E> collection) {
		this();
		incrementAll(collection, 1.0);
	}

	public void pruneKeysBelowThreshold(final double cutoff) {
		final Iterator<E> it = entries.keySet().iterator();
		while (it.hasNext()) {
			final E key = it.next();
			final double val = entries.get(key);
			if (val < cutoff) {
				it.remove();
			}
		}
		dirty = true;
	}

	public Set<Map.Entry<E, Double>> getEntrySet() {
		return entries.entrySet();
	}

	public boolean isEqualTo(final Counter<E> counter) {
		boolean tmp = true;
		final Counter<E> bigger = counter.size() > size() ? counter : this;
		for (final E e : bigger.keySet()) {
			tmp &= counter.getCount(e) == getCount(e);
		}
		return tmp;
	}

	public static void main(final String[] args) {
		final Counter<String> counter = new Counter<String>();
		System.out.println(counter);
		counter.incrementCount("planets", 7);
		System.out.println(counter);
		counter.incrementCount("planets", 1);
		System.out.println(counter);
		counter.setCount("suns", 1);
		System.out.println(counter);
		counter.setCount("aliens", 0);
		System.out.println(counter);
		System.out.println(counter.toString());
		System.out.println("Total: " + counter.totalCount());
	}

	public void clear() {
		entries.clear();
		dirty = true;
	}

	/**
	 * Sets all counts to the given value, but does not remove any keys
	 */
	public void setAllCounts(final double val) {
		for (final E e : keySet()) {
			setCount(e, val);
		}

	}

	public double dotProduct(final Counter<E> other) {
		double sum = 0.0;
		for (final Map.Entry<E, Double> entry : getEntrySet()) {
			final double otherCount = other.getCount(entry.getKey());
			if (otherCount == 0.0) continue;
			final double value = entry.getValue();
			if (value == 0.0) continue;
			sum += value * otherCount;

		}
		return sum;
	}

	public void scale(final double c) {

		for (final Map.Entry<E, Double> entry : getEntrySet()) {
			entry.setValue(entry.getValue() * c);
		}

	}

	public Counter<E> scaledClone(final double c) {
		final Counter<E> newCounter = new Counter<E>();

		for (final Map.Entry<E, Double> entry : getEntrySet()) {
			newCounter.setCount(entry.getKey(), entry.getValue() * c);
		}

		return newCounter;
	}

	public Counter<E> difference(final Counter<E> counter) {
		final Counter<E> clone = new Counter<E>(this);
		for (final E key : counter.keySet()) {
			final double count = counter.getCount(key);
			clone.incrementCount(key, -1 * count);
		}
		return clone;
	}

	public Counter<E> toLogSpace() {
		final Counter<E> newCounter = new Counter<E>(this);
		for (final E key : newCounter.keySet()) {
			newCounter.setCount(key, Math.log(getCount(key)));
		}
		return newCounter;
	}

	public boolean approxEquals(final Counter<E> other, final double tol) {
		for (final E key : keySet()) {
			if (Math.abs(getCount(key) - other.getCount(key)) > tol) {//
				return false;
			}
		}
		for (final E key : other.keySet()) {
			if (Math.abs(getCount(key) - other.getCount(key)) > tol) {
				//
				return false;
			}
		}
		return true;
	}

	public void setDirty(final boolean dirty) {
		this.dirty = dirty;
	}

	public Iterable<Double> values() {
		return new Iterable<Double>()
		{

			@Override
			public Iterator<Double> iterator() {

				return new Iterator<Double>()
				{
					Iterator<Entry<E, Double>> entryIterator = entrySet().iterator();

					@Override
					public boolean hasNext() {
						return entryIterator.hasNext();
					}

					@Override
					public Double next() {
						return entryIterator.next().getValue();
					}

					@Override
					public void remove() {
						entryIterator.remove();
					}
				};
			}
		};

	}

	public void prune(final Set<E> toRemove) {
		for (final E e : toRemove) {
			removeKey(e);
		}
	}

	public void pruneExcept(final Set<E> toKeep) {
		final List<E> toRemove = new ArrayList<E>();
		for (final E key : entries.keySet()) {
			if (!toKeep.contains(key)) toRemove.add(key);
		}
		for (final E e : toRemove) {
			removeKey(e);
		}
	}

	public static <L> Counter<L> absCounts(final Counter<L> counts) {
		final Counter<L> res = new Counter<L>();
		for (final Map.Entry<L, Double> entry : counts.entrySet()) {
			res.incrementCount(entry.getKey(), Math.abs(entry.getValue()));
		}
		return res;
	}

	// Compare by value.
	public class EntryValueComparator implements Comparator<Entry<E, Double>>
	{

		/**
		 * @param descending
		 */
		public EntryValueComparator(final boolean descending) {
			super();
			this.descending = descending;
		}

		private final boolean descending;

		@Override
		public int compare(final Entry<E, Double> e1, final Entry<E, Double> e2) {
			return descending ? Double.compare(e2.getValue(), e1.getValue()) : Double.compare(e1.getValue(), e2.getValue());
		}
	}

	public void putAll(final double d) {
		for (final Entry<E, Double> entry : entries.entrySet()) {

			setCount(entry.getKey(), d);
		}
		dirty = true;
	}

}
