package edu.stanford.nlp.mt.metrics;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;

public class InverseMetric<TK, FV> extends AbstractMetric<TK, FV> {
  private final EvaluationMetric<TK, FV> emetric;

  public InverseMetric(EvaluationMetric<TK, FV> emetric) {
    this.emetric = emetric;
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric() {
    return new IncrementalMetric();
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    return new IncrementalMetric();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    return emetric.getIncrementalMetricRecombinationFilter();
  }

  @Override
  public double maxScore() {
    return 0;
  }

  private class IncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    IncrementalEvaluationMetric<TK, FV> imetric = emetric
        .getIncrementalMetric();

    public String scoreDetails() {
      return "None";
    }
    
    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      imetric.add(trans);

      return this;
    }

    @Override
    public double maxScore() {
      return 0;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      imetric.replace(index, trans);
      return this;
    }

    @Override
    public double score() {
      return -imetric.score();
    }

    @Override
    public int size() {
      return imetric.size();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      if (o instanceof InverseMetric.IncrementalMetric) {
        return ((InverseMetric.IncrementalMetric) o).imetric
            .compareTo(imetric);
      }
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      return imetric.depth();
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      return -imetric.partialScore();
    }
  }
}
