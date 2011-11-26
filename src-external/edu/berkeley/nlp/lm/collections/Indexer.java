package edu.berkeley.nlp.lm.collections;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Semaphore;

/**
 * Maintains a two-way map between a set of objects and contiguous integers from
 * 0 to the number of objects.
 * 
 * @author Dan Klein
 * @authoer Adam Pauls
 */
@SuppressWarnings("rawtypes")
public class Indexer<E extends Comparable> implements Serializable
{
	private static final long serialVersionUID = -8769544079136550516L;

	protected ArrayList<E> objects;

	protected TIntMap<E> indexes;

	protected boolean locked = false;

	private Semaphore sem;

	public void lock() {
		this.locked = true;
	}

	public E getObject(final int index) {
		return objects.get(index);
	}

	public boolean add(final E elem) {
		final int oldSize = size();
		return getIndex(elem) >= oldSize;
	}

	/**
	 * Returns the number of objects indexed.
	 */
	public int size() {
		return objects.size();
	}

	/**
	 * Returns the index of the given object, or -1 if the object is not present
	 * in the indexer.
	 * 
	 * @param o
	 */
	public int indexOf(final E o) {
		final int index = indexes.get(o, -1);

		return index;
	}

	/**
	 * Return the index of the element If doesn't exist, add it.
	 */
	public int getIndex(final E e) {
		if (e == null) return -1;

		if (sem != null) sem.acquireUninterruptibly();
		int index = indexes.get(e, -1);
		if (index < 0) {
			if (locked) throw new RuntimeException("Attempt to add to locked indexer");
			index = size();
			objects.add(e);
			indexes.put(e, index);
		}
		if (sem != null) sem.release();
		return index;
	}

	public Indexer(final boolean sync) {
		objects = new ArrayList<E>();
		indexes = new TIntMap<E>();
		this.sem = sync ? new Semaphore(1) : null;
	}

	public Indexer() {
		this(false);
	}

	public Indexer(final Collection<? extends E> c) {
		this();
		for (final E a : c)
			getIndex(a);
	}

	/**
	 * Save some space my compacting underlying maps and lists.
	 */
	public void trim() {
		objects.trimToSize();
		indexes.switchToSortedList();
	}

	public Iterable<E> getObjects() {
		return Collections.unmodifiableList(objects);
	}
}
