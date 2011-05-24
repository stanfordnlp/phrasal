package edu.berkeley.nlp.lm.collections;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * List which returns special boundary symbols when get() is called outside the
 * range of the list.
 * 
 * @author Dan Klein
 */
public class BoundedList<E> extends AbstractList<E>
{
	private E leftBoundary;

	private E rightBoundary;

	private List<E> list;

	/**
	 * Returns the object at the given index, provided the index is between 0
	 * (inclusive) and size() (exclusive). If the index is < 0, then a left
	 * boundary object is returned. If the index is >= size(), a right boundary
	 * object is returned. The default boundary objects are both null, unless
	 * other objects are specified on construction.
	 */
	@Override
	public E get(final int index) {
		if (index < 0) return leftBoundary;
		if (index >= list.size()) return rightBoundary;
		return list.get(index);
	}

	@Override
	public int size() {
		return list.size();
	}

	public BoundedList(final List<E> list, final E leftBoundary, final E rightBoundary) {
		this.list = list;
		this.leftBoundary = leftBoundary;
		this.rightBoundary = rightBoundary;
	}

	public BoundedList(final List<E> list, final E boundary) {
		this(list, boundary, boundary);
	}

	@Override
	public List<E> subList(final int fromIndex, final int toIndex) {
		final List<E> retVal = new ArrayList<E>(toIndex - fromIndex);
		for (int i = fromIndex; i < toIndex; ++i) {
			retVal.add(get(i));
		}
		return retVal;
	}

}
