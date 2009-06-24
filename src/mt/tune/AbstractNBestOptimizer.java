package mt.tune;

import mt.metrics.EvaluationMetric;
import mt.base.MosesNBestList;

import edu.stanford.nlp.util.IString;

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
