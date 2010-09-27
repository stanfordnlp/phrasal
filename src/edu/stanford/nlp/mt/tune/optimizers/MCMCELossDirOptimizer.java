package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.MutableDouble;

/**
 * @author danielcer
 */
public class MCMCELossDirOptimizer extends AbstractNBestOptimizer {

  public MCMCELossDirOptimizer(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    double eval;
    for (int iter = 0;; iter++) {
      double[] tset = { 1e-5, 1e-4, 0.001, 0.01, 0.1, 1, 10, 100, 1000, 1e4,
          1e5 };
      Counter<String> newWts = new ClassicCounter<String>(wts);
      for (double aTset : tset) {
        MERT.T = aTset;
        MutableDouble md = new MutableDouble();
        Counter<String> dE = new MCMCDerivative(mert, md).optimize(newWts);
        newWts = mert.lineSearch(nbest, newWts, dE, emetric);
        eval = MERT.evalAtPoint(nbest, newWts, emetric);
        System.err.printf("T:%e Eval: %.5f E(Eval): %.5f\n", aTset, eval,
            md.doubleValue());
      }
      double ssd = MERT.wtSsd(wts, newWts);

      eval = MERT.evalAtPoint(nbest, newWts, emetric);
      System.err.printf("line opt %d: eval: %e ssd: %e\n", iter, eval, ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
      wts = newWts;
    }
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }
}