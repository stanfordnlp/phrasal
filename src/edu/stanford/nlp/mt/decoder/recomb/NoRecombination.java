package edu.stanford.nlp.mt.decoder.recomb;

/**
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class NoRecombination<S> implements RecombinationFilter<S> {

	public Object clone() throws CloneNotSupportedException {
    return super.clone();
	}

	@Override
	public boolean combinable(S hypA, S hypB) {
		return false;
	}

	@Override
	public long recombinationHashCode(S hyp) {
		return hyp.hashCode();
	}
	
}
