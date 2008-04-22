package mt.reranker;

import java.util.List;
import java.util.Arrays;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.CGMinimizer;

/**
 * LogLinear Max Expected BLEU (MEB) Trainer
 *
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class LogLinearMEBLearner extends AbstractOneOfManyClassifier {
  class ObjF extends AbstractCachingDiffFunction {
    public int domainDimension() { return featureIndex.size(); }

    public void calculate(double[] testWts) {
      wts = testWts;
      value = 0.0;

      double[] oEV = new double[wts.length];
      double[] iEV = new double[wts.length];
      double[] bvEV = new double[wts.length];

      double C = 0.0001;

      for (int i = 0; i < wts.length; i++) value += C*(wts[i]*wts[i]);
      value *= lchl.size();

      for (CompactHypothesisList chl : lchl) {
        double[] probs = getProbs(chl);
        double[] bleus = chl.getScores();
        for (int j = 0; j < probs.length; j++) {
          if (j > 0 && bleus[j-1] > bleus[j]) break;
          value += -bleus[j]*probs[j];
        }

        int[] fIndex; float[] fValue;
        Arrays.fill(iEV, 0);
        for (int j = 0; j < probs.length; j++) {
           // Warning: here, it would be incorrect to have
           // the statement "if (j > 0 && bleus[j-1] > bleus[j]) break"
           fIndex = chl.getFIndices()[j];
           fValue = chl.getFValues()[j];
           for (int fI = 0; fI < fIndex.length; fI++)
             iEV[fIndex[fI]] += probs[j]*fValue[fI];
        }
        for (int j = 0; j < probs.length; j++) {
          if (j > 0 && bleus[j-1] > bleus[j]) break;
          for (int i = 0; i < oEV.length; i++) {
            oEV[i] += bleus[j]*probs[j]*iEV[i];
          }
        }

        for (int j = 0; j < probs.length; j++) {
           if (j > 0 && bleus[j-1] > bleus[j]) break;
           fIndex = chl.getFIndices()[j];
           fValue = chl.getFValues()[j];
           for (int fI = 0; fI < fIndex.length; fI++)
            bvEV[fIndex[fI]] += bleus[j]*probs[j]*fValue[fI];
        }
      }
      for (int i = 0; i < wts.length; i++) {
         derivative[i] = - bvEV[i] + oEV[i] + lchl.size()*C*2*wts[i];
      }
     // System.err.println("final val: "+value);
    }
  }

  public boolean isLogLinear() { return true; }

  public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    ObjF objF = new ObjF();
    Minimizer minim = new CGMinimizer(false);
    wts = minim.minimize(objF, 1e-4, wts);
  }
}
