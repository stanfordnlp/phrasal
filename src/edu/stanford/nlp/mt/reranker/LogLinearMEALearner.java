package edu.stanford.nlp.mt.reranker;

import java.util.List;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.CGMinimizer;

/**
 * LogLinear Max Expected Accuracy (MEA) Trainer
 * 
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class LogLinearMEALearner extends AbstractOneOfManyClassifier {
  class ObjF extends AbstractCachingDiffFunction {
    @Override
    public int domainDimension() {
      return featureIndex.size();
    }

    @Override
    public void calculate(double[] testWts) {
      wts = testWts;
      value = 0.0;
      double[] cEV = new double[wts.length];
      double[] aEV = new double[wts.length];

      double C = 0.0001;

      for (int i = 0; i < wts.length; i++)
        value += C * (wts[i] * wts[i]);
      value *= lchl.size();

      for (CompactHypothesisList chl : lchl) {
        double[] probs = getProbs(chl);
        value -= probs[0];

        int[] fIndex = chl.getFIndices()[0];
        float[] fValue = chl.getFValues()[0];
        for (int fI = 0; fI < fIndex.length; fI++)
          cEV[fIndex[fI]] += probs[0] * fValue[fI];

        for (int j = 0; j < probs.length; j++) {
          fIndex = chl.getFIndices()[j];
          fValue = chl.getFValues()[j];
          for (int fI = 0; fI < fIndex.length; fI++)
            aEV[fIndex[fI]] += probs[0] * probs[j] * fValue[fI];
        }
      }
      for (int i = 0; i < derivative.length; i++) {
        derivative[i] = -cEV[i] + aEV[i] + lchl.size() * C * 2 * wts[i];
      }
    }
  }

  @Override
  public boolean isLogLinear() {
    return true;
  }

  @Override
  public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    ObjF objF = new ObjF();
    Minimizer<DiffFunction> minim = new CGMinimizer(false);
    wts = minim.minimize(objF, 1e-4, wts);
  }
}
