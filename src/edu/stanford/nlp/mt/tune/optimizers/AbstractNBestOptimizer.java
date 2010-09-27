package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.tune.NBestOptimizer;
import edu.stanford.nlp.mt.base.MosesNBestList;
import edu.stanford.nlp.mt.base.IString;

import java.util.Random;

/**
 * @author Michel Galley, Daniel Cer
 */
public abstract class AbstractNBestOptimizer implements NBestOptimizer {

  protected final MosesNBestList nbest;

  protected final MERT mert;
  protected final Random random;
  protected final EvaluationMetric<IString, String> emetric;

  public boolean doNormalization() {
    return true;
  }

  public AbstractNBestOptimizer(MERT mert) {
    this.mert = mert;
    this.emetric = mert.emetric;
    this.random = mert.random;
    this.nbest = MERT.nbest;
  }
}
