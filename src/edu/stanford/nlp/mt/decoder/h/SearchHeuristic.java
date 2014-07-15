package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * A beam search future cost heuristic.
 * 
 * @author danielcer
 */
public interface SearchHeuristic<TK, FV> extends Cloneable {

  public Object clone() throws CloneNotSupportedException;

  /**
   * Create a new heuristic.
   * 
   * @param sourceSequence
   * @param sourceInputProperties
   * @param options
   * @param scorer
   * @param sourceInputId
   * @return
   */
  double getInitialHeuristic(Sequence<TK> sourceSequence,
      InputProperties sourceInputProperties, 
      List<List<ConcreteRule<TK,FV>>> options, Scorer<FV> scorer, int sourceInputId);

  /**
   * Compute the delta between this derivation and the last one.
   * 
   * @param newHypothesis
   * @param newCoverage
   * @return
   */
  double getHeuristicDelta(Derivation<TK, FV> newHypothesis,
      CoverageSet newCoverage);
}
