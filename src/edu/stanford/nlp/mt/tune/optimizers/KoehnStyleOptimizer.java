package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Optimization algorithm used by cmert included in Moses.
 * 
 * @author danielcer
 */
public class KoehnStyleOptimizer extends AbstractBatchOptimizer {

  static public final boolean DEBUG = false;

  public KoehnStyleOptimizer(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;

    for (double oldEval = Double.NEGATIVE_INFINITY;;) {
      Counter<String> wtsFromBestDir = null;
      double fromBestDirScore = Double.NEGATIVE_INFINITY;
      String bestDirName = null;
      assert (wts != null);
      for (String feature : wts.keySet()) {
        // if (DEBUG)
        System.out.printf("Searching %s\n", feature);
        Counter<String> dir = new ClassicCounter<String>();
        dir.incrementCount(feature, 1.0);
        Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
        double eval = MERT.evalAtPoint(nbest, newWts, emetric);
        if (DEBUG)
          System.out.printf("\t%e\n", eval);
        if (eval > fromBestDirScore) {
          fromBestDirScore = eval;
          wtsFromBestDir = newWts;
          bestDirName = feature;
        }
      }

      System.out.printf("Best dir: %s Global max along dir: %f\n", bestDirName,
          fromBestDirScore);
      wts = wtsFromBestDir;

      double eval = MERT.evalAtPoint(nbest, wts, emetric);
      if (Math.abs(eval - oldEval) < MERT.MIN_OBJECTIVE_DIFF)
        break;
      oldEval = eval;
    }

    return wts;
  }
}