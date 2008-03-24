package mt;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public interface SearchHeuristic<TK,FV> {
	
	/**
	 * Note reset semantics
	 * 
	 * @param sequence
	 * @param options TODO
	 * @return
	 */
	double getInitialHeuristic(Sequence<TK> sequence, List<ConcreteTranslationOption<TK>> options, int translationId);
	
	/**
	 * 
	 * @param newHypothesis
	 * @param newCoverage
	 * @return
	 */
	double getHeuristicDelta(Hypothesis<TK,FV> newHypothesis, CoverageSet newCoverage);
}
