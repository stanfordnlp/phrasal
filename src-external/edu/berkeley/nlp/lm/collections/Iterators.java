package edu.berkeley.nlp.lm.collections;

import java.util.Iterator;

/**
 * Utilities for dealing with Iterators
 * 
 * @author adampauls
 * 
 */
public class Iterators
{

	/**
	 * Wraps an Iterator as an Iterable
	 * 
	 * @param <T>
	 * @param it
	 */
	public static <T> Iterable<T> able(final Iterator<T> it) {
		return new Iterable<T>()
		{
			boolean used = false;

			@Override
			public Iterator<T> iterator() {
				if (used) throw new RuntimeException("One use iterable");
				used = true;
				return it;
			}
		};
	}

}
