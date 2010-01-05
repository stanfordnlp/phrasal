package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.base.MosesNBestList;
import edu.stanford.nlp.mt.base.IString;

import java.util.Random;

/**
 * @author Michel Galley
 */
abstract class AbstractNBestOptimizer implements NBestOptimizer {

  protected static MosesNBestList nbest;

  protected final UnsmoothedMERT mert;
  protected final Random random;
  protected final EvaluationMetric<IString,String> emetric;

  AbstractNBestOptimizer(UnsmoothedMERT mert) {
    this.mert = mert;
    this.emetric = mert.emetric;
    this.random = mert.random;
  }
}
