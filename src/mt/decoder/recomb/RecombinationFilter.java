package mt.decoder.recomb;

/**
 * 
 * @author danielcer
 */
public interface RecombinationFilter<S> extends Cloneable {
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
	
	public RecombinationFilter<S> clone();
}
