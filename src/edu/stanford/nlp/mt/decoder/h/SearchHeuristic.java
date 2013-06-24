package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Derivation;
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
      List<List<ConcreteRule<TK,FV>>> options, Scorer<FV> scorer, int translationId);

  /**
	 * 
	 */
  double getHeuristicDelta(Derivation<TK, FV> newHypothesis,
      CoverageSet newCoverage);
}
