package mt.decoder.recomb;



/**
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class NoRecombination<S> implements RecombinationFilter<S> {
	@Override
	public boolean combinable(S hypA, S hypB) {
		return false;
	}

	@Override
	public long recombinationHashCode(S hyp) {
		return hyp.hashCode();
	}
	
}
