package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.base.MosesNBestList;
import edu.stanford.nlp.mt.base.IString;

import java.util.Random;

/**
 * @author Michel Galley, Daniel Cer
 */
abstract class AbstractNBestOptimizer implements NBestOptimizer {

  protected final MosesNBestList nbest;

  protected final MERT mert;
  protected final Random random;
  protected final EvaluationMetric<IString,String> emetric;

  public boolean doNormalization() {
     return true;
  }
  
  AbstractNBestOptimizer(MERT mert) {
    this.mert = mert;
    this.emetric = mert.emetric;
    this.random = mert.random;
    this.nbest = mert.nbest;
  }
}
