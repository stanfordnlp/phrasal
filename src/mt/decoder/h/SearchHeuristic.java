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
 * @param <T>
 */
public interface SearchHeuristic<TK,FV> extends Cloneable {

	public SearchHeuristic<TK,FV> clone();
	
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
