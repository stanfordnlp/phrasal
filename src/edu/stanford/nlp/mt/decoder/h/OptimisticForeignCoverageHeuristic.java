package edu.stanford.nlp.mt.decoder.h;

import java.util.List;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class OptimisticForeignCoverageHeuristic<TK, FV> implements
    SearchHeuristic<TK, FV> {

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public double getHeuristicDelta(Derivation<TK, FV> newHypothesis,
      CoverageSet newCoverage) {

    int foreignLength = newHypothesis.sourceSequence.size();
    double scorePerTranslatedWord = newHypothesis.score
        / (foreignLength - newHypothesis.untranslatedSourceTokens);
    double newH = scorePerTranslatedWord * newHypothesis.untranslatedSourceTokens;

    double oldH = newHypothesis.parent.h;

    return newH - oldH;

  }

  @Override
  public double getInitialHeuristic(Sequence<TK> sourceSequence, InputProperties sourceInputProperties,
      List<List<ConcreteRule<TK,FV>>> options, Scorer<FV> scorer, int sourceInputId) {
    return 0;
  }
}
