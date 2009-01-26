package mt.decoder.recomb;

import mt.decoder.util.Hypothesis;



/**
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class NoRecombination<S> implements RecombinationFilter<S> {
	public RecombinationFilter<Hypothesis<TK,FV>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<TK,FV>>)super.clone(); 
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
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
