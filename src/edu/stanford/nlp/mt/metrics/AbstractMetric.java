package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

abstract public class AbstractMetric<TK, FV> implements
    EvaluationMetric<TK, FV> {

  @Override
  public double score(List<ScoredFeaturizedTranslation<TK,FV>> translations) {
    IncrementalEvaluationMetric<TK, FV> incMetric = getIncrementalMetric();
    for (ScoredFeaturizedTranslation<TK,FV> tran : translations) {
      incMetric.add(tran);
    }
    return incMetric.score();
  }

  public double scoreSeq(List<Sequence<TK>> translations) {
    IncrementalEvaluationMetric<TK, FV> incMetric = getIncrementalMetric();
    for (Sequence<TK> tran : translations) {
      incMetric.add(tran);
    }
    return incMetric.score();
  }


  @Override
  abstract public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric();
}
