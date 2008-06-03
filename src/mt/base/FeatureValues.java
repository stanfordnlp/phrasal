package mt.base;

import java.util.*;


import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author danielcer
 *
 */
public class FeatureValues {
	private FeatureValues() { }
	
	/**
	 * 
	 * @param <T>
	 * @param featureValues
	 * @return
	 */
	public static <T> List<FeatureValue<T>> combine(List<FeatureValue<T>> featureValues) {
		ClassicCounter<T> counter = new ClassicCounter<T>();
		for (FeatureValue<T> fv : featureValues) {
			counter.incrementCount(fv.name, fv.value);
		}
		Set<T> keys = new TreeSet<T>(counter.keySet());
		List<FeatureValue<T>> featureList = new ArrayList<FeatureValue<T>>(keys.size());
		for (T key : keys) {
			featureList.add(new FeatureValue<T>(key, counter.getCount(key)));
		}
		return featureList;
	}
	
	public static <T> Counter<T> toCounter(List<FeatureValue<T>> featureValues) {
		ClassicCounter<T> counter = new ClassicCounter<T>();
		for (FeatureValue<T> fv : featureValues) {
			counter.incrementCount(fv.name, fv.value);
		}
		return counter;
	}
}
