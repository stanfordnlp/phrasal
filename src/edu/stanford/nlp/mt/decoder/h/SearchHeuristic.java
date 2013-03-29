package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 */
public interface SearchHeuristic<TK, FV> extends Cloneable {

  public Object clone() throws CloneNotSupportedException;

  /**
   * Note reset semantics
   */
  double getInitialHeuristic(Sequence<TK> sequence,
      List<List<ConcreteTranslationOption<TK,FV>>> options, Scorer<FV> scorer, int translationId);

  /**
	 * 
	 */
  double getHeuristicDelta(Hypothesis<TK, FV> newHypothesis,
      CoverageSet newCoverage);
}
