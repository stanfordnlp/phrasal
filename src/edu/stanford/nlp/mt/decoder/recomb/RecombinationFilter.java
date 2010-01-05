package edu.stanford.nlp.mt.decoder.recomb;

/**
 * 
 * @author danielcer
 */
public interface RecombinationFilter<S> extends Cloneable {
	/**
	 * 
	 */
	boolean combinable(S hypA, S hypB);
	
	/**
	 * 
	 */
	long recombinationHashCode(S hyp);
	
	public RecombinationFilter<S> clone();
}
