package mt.decoder.h;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.Sequence;
import mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 */
public interface SearchHeuristic<TK,FV> extends Cloneable {

	public SearchHeuristic<TK,FV> clone();
	
	/**
	 * Note reset semantics
	 * 
	 * @param options TODO
	 */
	double getInitialHeuristic(Sequence<TK> sequence, List<List<ConcreteTranslationOption<TK>>> options, int translationId);

	/**
	 * 
	 */
	double getHeuristicDelta(Hypothesis<TK,FV> newHypothesis, CoverageSet newCoverage);
}
