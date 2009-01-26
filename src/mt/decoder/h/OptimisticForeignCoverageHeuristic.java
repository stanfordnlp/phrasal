package mt.decoder.h;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.Sequence;
import mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class OptimisticForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

	public SearchHeuristic<TK,FV> clone() {
	   try {
	  	 return (SearchHeuristic<TK,FV>) super.clone();
	   } catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
	
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
