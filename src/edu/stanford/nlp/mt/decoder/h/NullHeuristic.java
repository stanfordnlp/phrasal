package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * 
 * @author Daniel Cer
 */
public class NullHeuristic<TK,FV> implements SearchHeuristic<TK,FV> {

	@SuppressWarnings("unchecked")
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
	public double getInitialHeuristic(Sequence<TK> sequence, List<List<ConcreteTranslationOption<TK>>> options, int translationId) {
		return 0;
	}

}
