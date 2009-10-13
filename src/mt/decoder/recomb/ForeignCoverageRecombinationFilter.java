package mt.decoder.recomb;

import mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class ForeignCoverageRecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

	@SuppressWarnings("unchecked")
	public RecombinationFilter<Hypothesis<TK,FV>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<TK,FV>>)super.clone(); 
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
	@Override
	public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
		return hypA.foreignCoverage.equals(hypB.foreignCoverage);
	}

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
		return hyp.foreignCoverage.hashCode();
	}

}
