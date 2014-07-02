package edu.stanford.nlp.mt.metrics;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;

/**
 * 
 * @author danielcer
 */
public class GeometricMeanCombinationMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final double[] weights;
  final EvaluationMetric<TK, FV>[] metrics;
  
  public GeometricMeanCombinationMetric(EvaluationMetric<TK, FV>... metrics) {
    this(null, metrics);
  }
  
  public GeometricMeanCombinationMetric(double[] weights,
      EvaluationMetric<TK, FV>... metrics) {
     if (weights == null) {
       weights = new double[metrics.length];
       for (int i = 0; i < weights.length; i++) {
         weights[i] = 1.0/metrics.length;
       }
     }
     this.weights = weights;
     this.metrics = metrics;   
  }
  
  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException(); 
  }

  @Override
  public double maxScore() {
    return 1.0;
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric() {
    return new IncrementalMetric();
  }

  class IncrementalMetric implements IncrementalEvaluationMetric<TK, FV> {
    final List<IncrementalEvaluationMetric<TK,FV>> incMetrics;
    
    public IncrementalMetric() {
      incMetrics = new ArrayList<IncrementalEvaluationMetric<TK,FV>>();
      for (int i = 0; i < metrics.length; i++) {
        incMetrics.add(metrics[i].getIncrementalMetric());
      }
    }
    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      throw new UnsupportedOperationException();
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      for (IncrementalEvaluationMetric<TK, FV> incMetric : incMetrics) {
        incMetric.add(trans);
      }
      return this;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      for (IncrementalEvaluationMetric<TK, FV> incMetric : incMetrics) {
        incMetric.replace(index, trans);
      }
      return this;
    }

    @Override
    public double score() {
      double weightedLogSum = 0;
      for (int i = 0; i < incMetrics.size(); i++) {
        IncrementalEvaluationMetric<TK, FV> incMetric = incMetrics.get(i);
        double score = incMetric.score();
        if (score < 0.0) {
            if (score < -1.0) score = -1.0;
             score = (1.0 + score);
        }
        weightedLogSum += weights[i]*Math.log(score);
      }
      return Math.exp(weightedLogSum);
    }

    @Override
    public double maxScore() {
      return 1.0;
    }

    @Override
    public int size() {
      return incMetrics.get(0).size();
    }
    
    @Override
    public Object clone() throws CloneNotSupportedException {
      return new UnsupportedOperationException();
    }
    
    @Override
    public String scoreDetails() {
      StringBuilder sbuilder = new StringBuilder();
      
      for (int i = 0; i < incMetrics.size(); i++) {
        IncrementalEvaluationMetric<TK, FV> incMetric = incMetrics.get(i);
        double origScore = incMetric.score();
        double score = origScore;
        if (score < 0.0) {
            if (score < -1.0) score = -1.0;
             score = (1.0 + score);
        }
        //weightedLogSum += weights[i]*Math.log(score);
        if (score == origScore) {
          sbuilder.append(String.format("\t%.3f <= %.3f * log(%s (%.3f))\n", 
            weights[i]*Math.log(score), weights[i],
            metrics[i].getClass().toString().toString().replaceAll("^.*metrics\\.", ""), origScore));
        } else {
          sbuilder.append(String.format("\t%.3f <= %.3f * log(%s (1.0%.3f))\n", 
              weights[i]*Math.log(score), weights[i],
              metrics[i].getClass().toString().toString().replaceAll("^.*metrics\\.", ""), origScore));
        }
      }
      return sbuilder.toString();
    }
};

}