package mt;

/**
 * 
 * @author danielcer
 */
public interface RecombinationFilter<S> {
	/**
	 * 
	 * @param hypA
	 * @param hypB
	 * @return
	 */
	boolean combinable(S hypA, S hypB);
	
	/**
	 * 
	 * @param hyp
	 * @return
	 */
	long recombinationHashCode(S hyp);
}
