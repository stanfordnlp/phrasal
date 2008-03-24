package mt;

import edu.stanford.nlp.util.IndexInterface;

/**
 * 
 * @author danielcer
 *
 */
public class Sequences {
	
	/**
	 * 
	 * @param <T>
	 * @param sequence
	 * @return
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
	 * @param sequence
	 * @param index
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> int[] toIntArray(Sequence<T> sequence, IndexInterface<T> index) {
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
}
