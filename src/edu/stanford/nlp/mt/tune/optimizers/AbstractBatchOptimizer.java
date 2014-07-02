package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.tune.BatchOptimizer;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IString;

import java.util.Random;

/**
 * @author Michel Galley, Daniel Cer
 */
public abstract class AbstractBatchOptimizer implements BatchOptimizer {

  protected final FlatNBestList nbest;

  protected final MERT mert;
  protected final Random random;
  protected final EvaluationMetric<IString, String> emetric;

  public boolean doNormalization() {
    return true;
  }

  public AbstractBatchOptimizer(MERT mert) {
    this.mert = mert;
    this.emetric = mert.emetric;
    this.random = mert.random;
    this.nbest = MERT.nbest;
  }
 
}
