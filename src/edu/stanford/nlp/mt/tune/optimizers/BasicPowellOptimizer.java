package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Powell's method, but without heuristics for replacement of search directions.
 * See Press et al Numerical Recipes (1992) pg 415
 * 
 * Unlike the heuristic version, see powell() below, this variant has quadratic
 * convergence guarantees. However, note that the heuristic version should do
 * better in long and narrow valleys.
 * 
 * @author danielcer
 */
public class BasicPowellOptimizer extends AbstractBatchOptimizer {

  static public final boolean DEBUG = false;

  public BasicPowellOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;

    // initialize search directions
    List<Counter<String>> axisDirs = new ArrayList<Counter<String>>(
        initialWts.size());
    List<String> featureNames = new ArrayList<String>(wts.keySet());
    Collections.sort(featureNames);
    for (String featureName : featureNames) {
      Counter<String> dir = new ClassicCounter<String>();
      dir.incrementCount(featureName);
      axisDirs.add(dir);
    }

    // main optimization loop
    Counter[] p = new ClassicCounter[axisDirs.size()];
    double objValue = MERT.evalAtPoint(nbest, wts, emetric); // obj value w/o
    // smoothing
    List<Counter<String>> dirs = null;
    for (int iter = 0;; iter++) {
      if (iter % p.length == 0) {
        // reset after N iterations to avoid linearly dependent search
        // directions
        System.err.printf("%d: Search direction reset\n", iter);
        dirs = new ArrayList<Counter<String>>(axisDirs);
      }
      // search along each direction
      assert (dirs != null);
      p[0] = mert.lineSearch(nbest, wts, dirs.get(0), emetric);
      for (int i = 1; i < p.length; i++) {
        p[i] = mert.lineSearch(nbest, (Counter<String>) p[i - 1], dirs.get(i),
            emetric);
        dirs.set(i - 1, dirs.get(i)); // shift search directions
      }

      double totalWin = MERT.evalAtPoint(nbest, p[p.length - 1], emetric)
          - objValue;
      System.err.printf("%d: totalWin: %e Objective: %e\n", iter, totalWin,
          objValue);
      if (Math.abs(totalWin) < MERT.MIN_OBJECTIVE_DIFF)
        break;

      // construct combined direction
      Counter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

      dirs.set(p.length - 1, combinedDir);

      // search along combined direction
      wts = mert.lineSearch(nbest, (Counter<String>) p[p.length - 1],
          dirs.get(p.length - 1), emetric);
      objValue = MERT.evalAtPoint(nbest, wts, emetric);
      System.err.printf("%d: Objective after combined search %e\n", iter,
          objValue);
    }

    return wts;
  }
}