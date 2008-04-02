package mt.decoder.util;



/**
 * 
 * @author danielcer
 *
 */
public interface Beam<S extends State<S>> extends Iterable<S> {
	/**
	 * 
	 * @param state
	 * @return
	 */
	S put(S state);
	
	/**
	 * 
	 * @return
	 */
	S remove();
	
	/**
	 * 
	 * @return
	 */
	S removeWorst();
	
	
	/**
	 * 
	 * @return
	 */
	int size();
	
	/**
	 * 
	 * @return
	 */
	int capacity();
	
	/**
	 * 
	 * @return
	 */
	double bestScore();
	
	/**
	 * 
	 * @return
	 */
	public int recombined();
	
	/**
	 * 
	 * @return
	 */
	public int preinsertionDiscarded();
	
	/**
	 * 
	 * @return
	 */
	public int pruned();
	
}
