package edu.stanford.nlp.mt.metrics;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

public class ScorerWrapperEvaluationMetric<TK, FV> implements
    EvaluationMetric<TK, FV> {

  private final Scorer<FV> scorer;

  public ScorerWrapperEvaluationMetric(Scorer<FV> scorer) {
    this.scorer = scorer;
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric() {
    return new EvaluationIncMetric();
  }

  private class EvaluationIncMetric implements
      IncrementalEvaluationMetric<TK, FV> {

    private double score = 0;
    List<Double> scores = new ArrayList<Double>();

    @Override
    public String scoreDetails() {
      return "None";
    }
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      double tscore;
      score += tscore = scorer.getIncrementalScore(trans.features);
      scores.add(tscore);
      return this;
    }
    
    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> trans) {
      // Apparently we need the features?!
      throw new UnsupportedOperationException();
    }

    @Override
    public double maxScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      double tscore;
      score -= scores.get(index);
      score += tscore = scorer.getIncrementalScore(trans.features);
      scores.set(index, tscore);
      return this;
    }

    @Override
    public double score() {
      return score;
    }

    @Override
    public int size() {
      return scores.size();
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      throw new UnsupportedOperationException();
    }

  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    return new EvaluationIncMetric();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double maxScore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double score(List<ScoredFeaturizedTranslation<TK, FV>> sequences) {
    throw new UnsupportedOperationException();
  }
  @Override
  public double scoreSeq(List<Sequence<TK>> sequences) {
    throw new UnsupportedOperationException();
  }

}
