package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ErasureUtils;

/**
 * @author danielcer
 */
public class CerStyleOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = true;

  public CerStyleOptimizer(MERT mert) {
    super(mert);
  }

  Random random = new Random();
  
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    double oldEval = Double.NEGATIVE_INFINITY;
    double finalEval;
    int iter = 0;

    double initialEval = MERT.evalAtPoint(nbest, wts, emetric);
    System.out.printf("Initial (pre-optimization) Score: %f\n", initialEval);

    MERT.normalize(wts);

    for (;; iter++) {
      Counter<String> dEl = new ClassicCounter<String>();
      double bestEval = Double.NEGATIVE_INFINITY;
      Counter<String> nextWts = wts;
      //List<Counter<String>> priorSearchDirs = new ArrayList<Counter<String>>();
      // priorSearchDirs.add(wts);      
      for (int noProgressCnt = 0; noProgressCnt < 15; ) {
        synchronized (random) {
          for (String wt : wts.keySet()) {                   
            dEl.setCount(wt, random.nextDouble()-0.5);
          }
        }
        MERT.normalize(dEl);
        Counter<String> searchDir = new ClassicCounter<String>(dEl);
        /*
        for (Counter<String> priorDir : priorSearchDirs) {
          Counter<String> projOnPrior = new ClassicCounter<String>(priorDir);
          Counters.multiplyInPlace(
              projOnPrior,
              Counters.dotProduct(priorDir, dEl)
                  / Counters.dotProduct(priorDir, priorDir));
          Counters.subtractInPlace(searchDir, projOnPrior);
        }
        if (Counters.dotProduct(searchDir, searchDir) < MERT.NO_PROGRESS_SSD) {
          noProgressCnt++;
          continue;
        }
        priorSearchDirs.add(searchDir);
        */        
        if (DEBUG) {
          System.out.printf("Searching %s\n", searchDir);
        }
        nextWts = mert.lineSearch(nbest, nextWts, searchDir, emetric);
        double eval = MERT.evalAtPoint(nbest, nextWts, emetric);
        if (DEBUG) {
          System.out.printf("  evalAtNew point: %e\n", eval); 
        }
        if (Math.abs(eval - bestEval) < 1e-9) {
          noProgressCnt++;
        } else {
          noProgressCnt = 0;
        }

        bestEval = eval;
      }

      MERT.normalize(nextWts);
      double eval;
      Counter<String> oldWts = wts;
      eval = bestEval;
      wts = nextWts;

      double ssd = 0;
      for (String k : wts.keySet()) {
        double diff = oldWts.getCount(k) - wts.getCount(k);
        ssd += diff * diff;
      }
      ErasureUtils.noop(ssd);

      System.out
          .printf(
              "Global max along dEl dir(%d): %f obj diff: %f (*-1+%f=%f) Total Cnt: %f l1norm: %f\n",
              iter, eval, Math.abs(oldEval - eval), MERT.MIN_OBJECTIVE_DIFF,
              MERT.MIN_OBJECTIVE_DIFF - Math.abs(oldEval - eval),
              wts.totalCount(), MERT.l1norm(wts));

      if (Math.abs(oldEval - eval) < MERT.MIN_OBJECTIVE_DIFF) {
        finalEval = eval;
        break;
      }

      oldEval = eval;
    }

    System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
    return wts;
  }
}