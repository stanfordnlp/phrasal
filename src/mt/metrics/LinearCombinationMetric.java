package mt.metrics;

import mt.base.*;
import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.util.State;
import mt.metrics.IncrementalEvaluationMetric;

import java.util.ArrayList;
import java.util.List;


/**
 * Linear combination of any number of metrics, e.g., BLEU minus TER.
 * Note that this class does not support some of the advanced features of
 * BLEUMetric, such as recombination based on identical ngram counts.
 * 
 * @author Michel Galley
 */
public class LinearCombinationMetric<TK,FV> extends AbstractMetric<TK,FV> {

  final double[] weights;
  final AbstractMetric<TK,FV>[] metrics;

  public LinearCombinationMetric(double[] weights, AbstractMetric<TK,FV>... metrics) {
    if(weights.length != metrics.length)
      throw new IllegalArgumentException();
    this.weights = weights;
    this.metrics = metrics;
  }

  public double maxScore() {
    double maxScore = 0.0;
    for(int i=0; i<weights.length; ++i)
      maxScore += weights[i]*metrics[i].maxScore();
    return maxScore;
  }

  @Override
	public LCIncrementalMetric getIncrementalMetric() {
		return new LCIncrementalMetric();
	}

  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(NBestListContainer<TK, FV> nbestList) {
    return new LCIncrementalMetric(nbestList);
  }

  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  public class LCIncrementalMetric implements IncrementalEvaluationMetric<TK,FV> {

    List<IncrementalEvaluationMetric<TK,FV>> iems = new ArrayList<IncrementalEvaluationMetric<TK,FV>>();

    LCIncrementalMetric() {
      for(AbstractMetric<TK,FV> metric :  metrics)
        iems.add(metric.getIncrementalMetric());
    }

    LCIncrementalMetric(LCIncrementalMetric o) {
      List<IncrementalEvaluationMetric<TK,FV>> oiems = o.iems;
      for(IncrementalEvaluationMetric<TK,FV> oiem : oiems) {
        iems.add(oiem.clone()); 
      }
    }

    LCIncrementalMetric(NBestListContainer<TK, FV> nbest) {
      for(AbstractMetric<TK,FV> metric :  metrics)
        iems.add(metric.getIncrementalMetric(nbest));
    }

    public IncrementalEvaluationMetric<TK, FV> add(ScoredFeaturizedTranslation<TK, FV> trans) {
      for(IncrementalEvaluationMetric<TK,FV> iem :  iems)
        iem.add(trans);
      return this;
    }

    public IncrementalEvaluationMetric<TK, FV> replace(int index, ScoredFeaturizedTranslation<TK, FV> trans) {
      for(IncrementalEvaluationMetric<TK,FV> iem :  iems)
        iem.replace(index, trans);
      return this;
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    public double score() {
      double score = 0.0;
      for(int i=0; i<weights.length; ++i)
        score += weights[i]*iems.get(i).score();
      return score;
    }

    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    public int depth() {
      throw new UnsupportedOperationException();
    }

    public double maxScore() {
      double maxScore = 0.0;
      for(int i=0; i<weights.length; ++i)
        maxScore += weights[i]*iems.get(i).maxScore();
      return maxScore;
    }

    private boolean checkSize(int size) {
      for(int i=1; i<iems.size(); ++i)
        if(iems.get(i).size() != size)
          return false;
      return true;
    }

    public int size() {
      int sz = iems.get(0).size();
      assert(checkSize(sz));
      return sz;
    }

    @Override
		public IncrementalEvaluationMetric<TK, FV> clone() {
      return new LCIncrementalMetric(this);
    }
  }
}
