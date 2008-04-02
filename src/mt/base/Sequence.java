package mt.base;


/**
 * Immutable sequence 
 * 
 * Contract:
 * 		- Implementations should provide cheap construction
 *        of subsequences.
 * 
 * Notes: In the future, Sequence may be made into a subtype of
 * java.util.Collection or java.util.List. However, right now
 * this would bring with it alot of methods that aren't really
 * useful given how sequences are used.
 *  
 * @author Daniel Cer
 *
 * @param <T>
 */
public interface Sequence<T> extends Iterable<T> {
	
	/**
	 * 
	 * @param i
	 * @return
	 */
	T get(int i);
	
	/**
	 * 
	 * @return
	 */
	int size();
	
	/**
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	public Sequence<T> subsequence(int start, int end);
	
	/**
	 * 
	 * @param select
	 * @return
	 */
	public Sequence<T> subsequence(CoverageSet select);
	
	/**
	 * 
	 * @return
	 */
	public long longHashCode();
	
	/**
	 * 
	 * @param prefix
	 */
	public boolean startsWith(Sequence<T> prefix);
	
	/**
	 * 
	 * @param subsequence
	 * @return
	 */
	public boolean contains(Sequence<T> subsequence);
	
	public String toString(String delim);
	
	public String toString(String prefix, String delim);
	
	public String toString(String prefix, String delimeter, String suffix);
}
