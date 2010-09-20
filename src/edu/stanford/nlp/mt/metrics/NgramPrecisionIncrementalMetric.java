package edu.stanford.nlp.mt.metrics;

/**
 * @author Michel Galley
 */
public interface NgramPrecisionIncrementalMetric<TK, FV> extends
    IncrementalEvaluationMetric<TK, FV> {

  public double[] precisions();

  // public double ngramPrecisionScore();

  public double brevityPenalty();

}
