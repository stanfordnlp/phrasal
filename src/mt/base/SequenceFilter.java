package mt.base;


/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public interface SequenceFilter<TK> {
	
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	boolean accepts(Sequence<TK> sequence);
}
