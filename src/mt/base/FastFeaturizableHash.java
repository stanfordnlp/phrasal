package mt.base;

import java.util.ArrayList;

import mt.decoder.util.Hypothesis;
import edu.stanford.nlp.util.IString;

/**
 * 
 * @author danielcer
 *
 * @param <V>
 */
public class FastFeaturizableHash<V> {
	final long offset;
	final ArrayList<V> values; 

	/**
	 * 
	 */
	public FastFeaturizableHash() {
		offset = Hypothesis.nextId;
		values = new ArrayList<V>(500000);
	}
	
	/**
	 * 
	 * @param value
	 */
	public void put(Featurizable<IString,String> f, V value) {
		int idx = (int)(f.hyp.id-offset);
		values.ensureCapacity(idx+1);
		while (values.size() <= idx) values.add(null);
		values.set(idx, value);
	}
	
	/**
	 * 
	 * @return
	 */
	public V get(Featurizable<IString,String> f) {
		if (f == null) return null;
		int idx = (int)(f.hyp.id-offset);
		if (idx >= values.size()) return null;
		return values.get(idx);
	}
}
