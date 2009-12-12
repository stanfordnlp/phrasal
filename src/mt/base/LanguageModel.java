package mt.base;


/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public interface LanguageModel<T> {
	
	/**
	 * 
	 * @param sequence
	 */
	double score(Sequence<T> sequence);
	
	/**
	 * 
	 */
	T getStartToken();
	
	/**
	 * 
	 */
	T getEndToken();
	
	/**
	 * 
	 */
	String getName();
	
	/**
	 * 
	 */
	int order();
	
	/**
	 * 
	 * @param sequence
	 */
	boolean releventPrefix(Sequence<T> sequence);
}
