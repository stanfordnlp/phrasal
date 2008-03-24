package mt;

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
	 * @return
	 */
	double score(Sequence<T> sequence);
	
	/**
	 * 
	 * @return
	 */
	T getStartToken();
	
	/**
	 * 
	 * @return
	 */
	T getEndToken();
	
	/**
	 * 
	 * @return
	 */
	String getName();
	
	/**
	 * 
	 * @return
	 */
	int order();
	
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	boolean releventPrefix(Sequence<T> sequence);
}
