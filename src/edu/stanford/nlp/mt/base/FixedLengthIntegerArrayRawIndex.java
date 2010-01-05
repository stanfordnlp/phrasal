package edu.stanford.nlp.mt.base;

import java.util.*;

public class FixedLengthIntegerArrayRawIndex implements IntegerArrayRawIndex {
	final int[] rawInts;
	final int arraySize;
	final int indexSize;
	final int mask;
	final BitSet used;
	
	static int totalWordsAllocated = 0;
	
	public FixedLengthIntegerArrayRawIndex(int arraySize, int log2IndexSize) {

		indexSize = 1<<log2IndexSize;		
		mask = indexSize-1;
		totalWordsAllocated += arraySize*indexSize;
		rawInts = new int[arraySize*indexSize];
		used = new BitSet(indexSize);
		this.arraySize = arraySize;
	}

  private int initialSearchIndex(int[] array) {
		long index = 0;
		long mul = 0x5DEECE66DL;
		for (int el : array) {
			index = mul*index + 0xBL;
			index += mul*el + 0xBL;
		}
		return (int)(index>>32);
	}
	
	private boolean matchAt(int[] array, int index) {
		int pos = index * arraySize;
		for (int i = 0; i < array.length; i++) {
			if (array[i] != rawInts[pos++]) return false;
		}
		return true;
	}

	/**
	 * 
	 */
	public int getIndex(int[] array) {
		return getIndex(array, initialSearchIndex(array));
	}

	private int getIndex(int[] array, int initIndex) {
		int index = initIndex;

		for (int i = 0; i < indexSize; i++, index++) {
			// index = initIndex + i>>1 + (i*i)>>2; quadratic probe
			index = index & mask;
			if (!used.get(index)) return -index-1;
			if (matchAt(array, index)) return index;
		}
		
		throw new RuntimeException("IntegerArrayRawIndex is full");
	}
	
	/**
	 * 
	 */
	public synchronized int insertIntoIndex(int[] array) {
		int initIndex = initialSearchIndex(array);
		int index = getIndex(array, initIndex);
		if (index >= 0) return index;
		index = -index-1;
		int pos = index * arraySize;
		for (int i = 0; i < array.length; i++) {
			 rawInts[i+pos] = array[i];
		}
		used.set(index);
		return index;
	}
}
