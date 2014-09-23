package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Powell's Method
 * 
 * A typical implementation - with details originally based on David Chiang's
 * CMERT 0.5 (as distributed with Moses 1.5.8)
 * 
 * This implementation appears to be based on that given in Press et al's
 * Numerical Recipes (1992) pg. 417.
 * 
 * @author danielcer
 */
public class PowellOptimizer extends AbstractBatchOptimizer {

  static public final boolean DEBUG = false;

  public PowellOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    // initialize search directions
    List<Counter<String>> dirs = new ArrayList<Counter<String>>(
        initialWts.size());
    List<String> featureNames = new ArrayList<String>(wts.keySet());
    Collections.sort(featureNames);
    for (String featureName : featureNames) {
      Counter<String> dir = new ClassicCounter<String>();
      dir.incrementCount(featureName);
      dirs.add(dir);
    }

    // main optimization loop
    Counter[] p = new ClassicCounter[dirs.size()];
    double objValue = MERT.evalAtPoint(nbest, wts, emetric); // obj value w/o
    // smoothing
    for (int iter = 0;; iter++) {
      // search along each direction
      p[0] = mert.lineSearch(nbest, wts, dirs.get(0), emetric);
      double eval = MERT.evalAtPoint(nbest, p[0], emetric);
      double biggestWin = Math.max(0, eval - objValue);
      System.err.printf("initial totalWin: %e (%e-%e)\n", biggestWin, eval,
          objValue);
      System.err.printf("apply @ wts: %e\n",
          MERT.evalAtPoint(nbest, wts, emetric));
      System.err.printf("apply @ p[0]: %e\n",
          MERT.evalAtPoint(nbest, p[0], emetric));
      objValue = eval;
      int biggestWinId = 0;
      double totalWin = biggestWin;
      double initObjValue = objValue;
      for (int i = 1; i < p.length; i++) {
        p[i] = mert.lineSearch(nbest, (Counter<String>) p[i - 1], dirs.get(i),
            emetric);
        eval = MERT.evalAtPoint(nbest, p[i], emetric);
        if (Math.max(0, eval - objValue) > biggestWin) {
          biggestWin = eval - objValue;
          biggestWinId = i;
        }
        totalWin += Math.max(0, eval - objValue);
        System.err.printf("\t%d totalWin: %e(%e-%e)\n", i, totalWin, eval,
            objValue);
        objValue = eval;
      }

      System.err.printf("%d: totalWin %e biggestWin: %e objValue: %e\n", iter,
          totalWin, biggestWin, objValue);

      // construct combined direction
      Counter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

      // check to see if we should replace the dominant 'win' direction
      // during the last iteration of search with the combined search direction
      Counter<String> testPoint = new ClassicCounter<String>(p[p.length - 1]);
      testPoint.addAll(combinedDir);
      double testPointEval = MERT.evalAtPoint(nbest, testPoint, emetric);
      double extrapolatedWin = testPointEval - objValue;
      System.err.printf("Test Point Eval: %e, extrapolated win: %e\n",
          testPointEval, extrapolatedWin);
      if (extrapolatedWin > 0
          && 2 * (2 * totalWin - extrapolatedWin)
              * Math.pow(totalWin - biggestWin, 2.0) < Math.pow(
              extrapolatedWin, 2.0) * biggestWin) {
        System.err.printf(
            "%d: updating direction %d with combined search dir\n", iter,
            biggestWinId);
        MERT.normalize(combinedDir);
        dirs.set(biggestWinId, combinedDir);
      }

      // Search along combined dir even if replacement didn't happen
      wts = mert.lineSearch(nbest, p[p.length - 1], combinedDir, emetric);
      eval = MERT.evalAtPoint(nbest, wts, emetric);
      System.err.printf(
          "%d: Objective after combined search (gain: %e prior:%e)\n", iter,
          eval - objValue, objValue);

      objValue = eval;

      double finalObjValue = objValue;
      System.err.printf("Actual win: %e (%e-%e)\n", finalObjValue
          - initObjValue, finalObjValue, initObjValue);
      if (Math.abs(initObjValue - finalObjValue) < MERT.MIN_OBJECTIVE_DIFF)
        break; // changed to prevent infinite loops
    }

    return wts;
  }
}