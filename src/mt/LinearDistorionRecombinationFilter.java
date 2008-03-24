package mt;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class LinearDistorionRecombinationFilter<TK, FV> implements RecombinationFilter<Hypothesis<TK, FV>> {

	/**
	 * 
	 * @param hyp
	 * @return
	 */
	private int lastOptionForeignEdge(Hypothesis<TK,FV> hyp) {
		if (hyp.translationOpt == null) {
			return 0;
		}
		return hyp.translationOpt.foreignPos + hyp.translationOpt.abstractOption.foreign.size(); 
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
