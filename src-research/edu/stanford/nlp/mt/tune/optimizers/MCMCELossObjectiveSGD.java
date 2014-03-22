package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.MutableDouble;

/**
 * @author danielcer
 */
public class MCMCELossObjectiveSGD extends AbstractBatchOptimizer {

  static final int DEFAULT_MAX_ITER_SGD = 1000;

  int max_iter;

  public MCMCELossObjectiveSGD(MERT mert) {
    this(mert, DEFAULT_MAX_ITER_SGD);
  }

  public MCMCELossObjectiveSGD(MERT mert, int max_iter) {
    super(mert);
    this.max_iter = max_iter;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = new ClassicCounter<String>(initialWts);
    double eval = 0;
    double lastExpectedEval = Double.NEGATIVE_INFINITY;
    double lastObj = Double.NEGATIVE_INFINITY;
    double[] objDiffWin = new double[10];

    for (int iter = 0; iter < max_iter; iter++) {
      MutableDouble expectedEval = new MutableDouble();
      MutableDouble objValue = new MutableDouble();

      Counter<String> dE = new MCMCDerivative(mert, expectedEval, objValue)
          .optimize(wts);
      Counters.multiplyInPlace(dE, -1.0 * MERT.lrate);
      wts.addAll(dE);

      double ssd = Counters.L2Norm(dE);
      double expectedEvalDiff = expectedEval.doubleValue() - lastExpectedEval;
      double objDiff = objValue.doubleValue() - lastObj;
      lastObj = objValue.doubleValue();
      objDiffWin[iter % objDiffWin.length] = objDiff;
      double winObjDiff = Double.POSITIVE_INFINITY;
      if (iter > objDiffWin.length) {
        double sum = 0;
        for (double anObjDiffWin : objDiffWin) {
          sum += anObjDiffWin;
        }
        winObjDiff = sum / objDiffWin.length;
      }
      lastExpectedEval = expectedEval.doubleValue();
      eval = MERT.evalAtPoint(nbest, wts, emetric);
      System.err
          .printf(
              "sgd step %d: eval: %e wts ssd: %e E(Eval): %e delta E(Eval): %e obj: %e (delta: %e)\n",
              iter, eval, ssd, expectedEval.doubleValue(), expectedEvalDiff,
              objValue.doubleValue(), objDiff);
      if (iter > objDiffWin.length) {
        System.err.printf("objDiffWin: %e\n", winObjDiff);
      }
      if (MERT.MIN_OBJECTIVE_CHANGE_SGD > Math.abs(winObjDiff))
        break;
    }
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }
}