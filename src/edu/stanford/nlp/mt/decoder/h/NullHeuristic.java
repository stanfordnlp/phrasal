package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author Daniel Cer
 */
public class NullHeuristic<TK, FV> implements SearchHeuristic<TK, FV> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public double getHeuristicDelta(Derivation<TK, FV> newHypothesis,
      CoverageSet newCoverage) {
    return 0;
  }

  @Override
  public double getInitialHeuristic(Sequence<TK> sequence,
      List<List<ConcreteRule<TK,FV>>> options, Scorer<FV> scorer, int translationId) {
    return 0;
  }
}
