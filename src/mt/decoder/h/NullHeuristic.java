package mt.decoder.h;

import java.util.List;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.Sequence;
import mt.decoder.util.Hypothesis;

/**
 * 
 * @author Daniel Cer
 *
 * @param <T>
 */
public class NullHeuristic<TK,FV> implements SearchHeuristic<TK,FV> {

	public SearchHeuristic<TK,FV> clone() {
	   try {
	  	 return (SearchHeuristic<TK,FV>) super.clone();
	   } catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
	@Override
	public double getHeuristicDelta(Hypothesis<TK,FV> newHypothesis, CoverageSet newCoverage) {
		return 0;
	}

	@Override
	public double getInitialHeuristic(Sequence<TK> sequence, List<ConcreteTranslationOption<TK>> options, int translationId) {
		return 0;
	}
	
}
