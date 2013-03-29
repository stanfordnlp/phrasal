package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
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
  public double getHeuristicDelta(Hypothesis<TK, FV> newHypothesis,
      CoverageSet newCoverage) {
    return 0;
  }

  @Override
  public double getInitialHeuristic(Sequence<TK> sequence,
      List<List<ConcreteTranslationOption<TK,FV>>> options, Scorer<FV> scorer, int translationId) {
    return 0;
  }
}
