package mt.base;


/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public interface SequenceFilter<TK> {
	
	/**
	 * 
	 * @param sequence
	 * @return
	 */
	boolean accepts(Sequence<TK> sequence);
}
