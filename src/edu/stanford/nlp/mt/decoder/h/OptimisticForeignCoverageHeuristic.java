package edu.stanford.nlp.mt.decoder.h;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class OptimisticForeignCoverageHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

  @Override
	public Object clone() throws CloneNotSupportedException {
    return super.clone();
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
	public double getInitialHeuristic(Sequence<TK> sequence, List<List<ConcreteTranslationOption<TK>>> options, int translationId) {
		return 0;
	}
}
