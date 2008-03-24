package mt;

import java.util.List;

/**
 * 
 * @author Daniel Cer
 *
 * @param <T>
 */
public class NullHeuristic<TK,FV> implements SearchHeuristic<TK,FV> {

	@Override
	public double getHeuristicDelta(Hypothesis<TK,FV> newHypothesis, CoverageSet newCoverage) {
		return 0;
	}

	@Override
	public double getInitialHeuristic(Sequence<TK> sequence, List<ConcreteTranslationOption<TK>> options, int translationId) {
		return 0;
	}
	
}
