package mt.reranker;

import java.util.List;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;

/**
 * LogLinear Max Conditional Likelihood (MCL) Trainer
 *
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class LogLinearMCLLearner extends AbstractOneOfManyClassifier {
  class ObjF extends AbstractCachingDiffFunction {
    @Override
		public int domainDimension() { return featureIndex.size(); }

    @Override
		public void calculate(double[] testWts) {
      wts = testWts;
      value = 0.0;
      double[] tV = new double[wts.length];
      double[] eV = new double[wts.length];
      double sigma2 = 2.0;

      for (int i = 0; i < wts.length; i++) value -= (wts[i]*wts[i])/(2*sigma2);
      value *= lchl.size();

      for (CompactHypothesisList chl : lchl) {
        double[] bleus = chl.getScores();
        int bestBleu = ArrayMath.argmax(bleus);
        double[] probs = getProbs(chl);
        value += Math.log(probs[bestBleu]);

        // True values
        int[] fIndex = chl.getFIndices()[bestBleu];
        float[] fValue = chl.getFValues()[bestBleu];
        for (int fI = 0; fI < fIndex.length; fI++)
           tV[fIndex[fI]] += fValue[fI];

        // Expected values
        for (int j = 0; j < probs.length; j++) {
           fIndex = chl.getFIndices()[j];
           fValue = chl.getFValues()[j];
           for (int fI = 0; fI < fIndex.length; fI++)
             eV[fIndex[fI]] += probs[j]*fValue[fI];
        }
      }

      for (int i = 0; i < derivative.length; i++) {
         derivative[i] = tV[i] - eV[i] - lchl.size()*wts[i]/sigma2;
         derivative[i] *= -1.0;
      //   if (i < 10)
      //   System.err.printf("\ti:%d t: %f e: %f\n", i, tV[i], eV[i]);
      }
      value *= -1.0;
    }
  }

  @Override
	public boolean isLogLinear() { return true; }

  @Override
	public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    ObjF objF = new ObjF();
    Minimizer<DiffFunction> minim = new QNMinimizer(objF);
    //DiffFunctionTester.test(objF);
    wts = minim.minimize(objF, 1e-4, wts);
  }
}
