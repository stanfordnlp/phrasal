package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 *
 */
public class Sequences {
	
	/**
	 * 
	 * @param <T>
	 */
	public static <T extends HasIntegerIdentity> int[] toIntArray(Sequence<T> sequence) {
		int sz = sequence.size();
		int[] intArray = new int[sz];
		for (int i = 0; i < sz; i++) {
			T token = sequence.get(i);
			intArray[i] = token.getId();
		}
		return intArray;
	}
	
	/**
	 * 
	 * @param <T>
	 */
	@SuppressWarnings("unchecked")
	public static <T> int[] toIntArray(Sequence<T> sequence, Index<T> index) {
		int sz = sequence.size();
		if (sz != 0) {
			if (sequence.get(0) instanceof HasIntegerIdentity) {
				return toIntArray((Sequence<HasIntegerIdentity>)sequence);
			}
		}
		int[] intArray = new int[sz];
		for (int i = 0; i < sz; i++) {
			T token = sequence.get(i);
			intArray[i] = index.indexOf(token, true);
		}
		return intArray;		
	}
	
	public static <T> boolean startsWith(Sequence<T> seq, Sequence<T> prefix) {
		int seqSz = seq.size();
		int prefixSz = prefix.size();
		if (prefixSz > seqSz) return false;
		for (int i = 0; i < prefixSz; i++) {
			if (!seq.get(i).equals(prefix.get(i))) return false; 
		}
		return true;
	}
}
