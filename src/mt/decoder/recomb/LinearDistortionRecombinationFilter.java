package mt.decoder.recomb;

import mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class LinearDistortionRecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

  @SuppressWarnings("unchecked")
	public RecombinationFilter<Hypothesis<TK,FV>> clone() {
		try {
			return (RecombinationFilter<Hypothesis<TK,FV>>)super.clone(); 
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
	/**
	 * 
	 */
	private int lastOptionForeignEdge(Hypothesis<TK,FV> hyp) {
		if (hyp.translationOpt == null) {
			return 0;
		}
    return hyp.translationOpt.foreignCoverage.length();
	}
	
	@Override
	public boolean combinable(Hypothesis<TK, FV> hypA, Hypothesis<TK, FV> hypB) {
		return lastOptionForeignEdge(hypA) == lastOptionForeignEdge(hypB);
	}

	@Override
	public long recombinationHashCode(Hypothesis<TK, FV> hyp) {
		return lastOptionForeignEdge(hyp);
	}

}
