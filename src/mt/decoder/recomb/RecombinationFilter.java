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
	 */
	boolean combinable(S hypA, S hypB);
	
	/**
	 * 
	 * @param hyp
	 */
	long recombinationHashCode(S hyp);
	
	public RecombinationFilter<S> clone();
}
