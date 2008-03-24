package mt;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author danielcer
 *
 * @param <T>
 */
public class FeatureValue<T> {
	public final double value;
	public final T name;

	static WeakHashMap<Object, WeakReference<Object>> nameCache = new WeakHashMap<Object, WeakReference<Object>>();
	
	/**
	 * @param name
	 * @param value
	 */
	@SuppressWarnings("unchecked")
	public FeatureValue(T name, double value, boolean cacheName) {
		this.value = value;
		if (cacheName) {
			WeakReference<Object> nameSub = nameCache.get(name);
			if (nameSub == null) {
				this.name = name;
				nameCache.put(name, new WeakReference<Object>(name));
			} else {
				this.name = (T)nameSub.get();
			}
		} else {
			this.name = name;
		}
	}	
	
	public FeatureValue(T name, double value) {
		this.name = name;
		this.value = value;
	}
	
	public String toString() {
		return String.format("%s:%f", name, value);
	}
}
