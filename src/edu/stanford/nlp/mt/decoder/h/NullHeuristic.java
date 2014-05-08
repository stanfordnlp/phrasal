package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.pt.ConcreteRule;

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
  public double getHeuristicDelta(Derivation<TK, FV> newDerivation,
      CoverageSet newCoverage) {
    return 0;
  }

  @Override
  public double getInitialHeuristic(Sequence<TK> sourceSequence, InputProperties sourceInputProperties,
      List<List<ConcreteRule<TK,FV>>> options, Scorer<FV> scorer, int sourceInputId) {
    return 0;
  }
}
