package mt;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class OptimisticForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

	@Override
	public double getHeuristicDelta(Hypothesis<TK, FV> newHypothesis,
			CoverageSet newCoverage) {
		
		int foreignLength = newHypothesis.foreignSequence.size();
		double scorePerTranslatedWord = newHypothesis.score / (foreignLength - newHypothesis.untranslatedTokens);
		double newH = scorePerTranslatedWord * newHypothesis.untranslatedTokens;
		
		double oldH = newHypothesis.preceedingHyp.h;
		
		return newH - oldH;
		
	}

	@Override
	public double getInitialHeuristic(Sequence<TK> sequence, List<ConcreteTranslationOption<TK>> options, int translationId) {
		return 0;
	}

}
