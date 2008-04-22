package mt.reranker;

import java.util.List;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.CGMinimizer;

/**
 * Score Regression Learner
 *
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class ScoreRegressionLearner extends AbstractOneOfManyClassifier {
  static final String RENORMALIZE_PROP = "ScoreRegression.renormalize";
  boolean renormalize = false;

  double[] getAllScores(CompactHypothesisList chl) {
    double[] scores = super.getAllScores(chl);
    ArrayMath.add(scores, wts[biasWtIdx]);
    return scores;
  }

  double[] getPredictedScores(CompactHypothesisList chl) {
    double[] scores = getAllScores(chl);
    for (int i = 0; i < scores.length; i++) {
      scores[i] = 1.0/(1.0+Math.exp(-1*scores[i]));
    }
    return scores;
  }

  int biasWtIdx;

  boolean renormalizeScores(double[] s, int effectiveLen) {
    double min = s[0], max = s[0];
    for (int i = 1; i < effectiveLen; i++) {
       if (s[i] == 0) continue; // hack for current sentence bleu
       if (s[i] < min) min = s[i];
       if (s[i] > max) max = s[i];
    }
    if (max == min) return false;
    double range = max - min;
    for (int i = 0; i < effectiveLen; i++) s[i] = (s[i] - min)/range;
    return true;

 /*
    System.out.println("--");
    for (int i = 0; i < s.length; i++) System.out.printf("\t%d:%.3f\n", i,s[i]);
    System.out.println();
*/
  }

  class ObjF extends AbstractCachingDiffFunction {
    public int domainDimension() { return wts.length; }

    public void calculate(double[] testWts) {
      wts = testWts;
      value = 0.0;
      double C = 5;

      for (int i = 0; i < wts.length; i++) value += C*(wts[i]*wts[i]);
      value *= lchl.size();

      for (int i = 0; i < wts.length; i++) {
        derivative[i] = lchl.size()*C*2*wts[i];
      }

      int mlen = 0;
      for (CompactHypothesisList chl : lchl) {
          int len = chl.getScores().length; if (len > mlen) mlen = len; }
      double[] bleus = new double[mlen];
      for (CompactHypothesisList chl : lchl) {
        double[] scores = getPredictedScores(chl);
        double[] origBleus = chl.getScores();
        for (int i = 0; i < origBleus.length; i++) bleus[i] = origBleus[i];
        if  (renormalize) if(!renormalizeScores(bleus, origBleus.length))
        continue;
        for (int idx = 0; idx < scores.length; idx++) {
           // temp hack for sentence bleu
           if (!renormalize && bleus[idx] == 0) continue;
           if (renormalize && bleus[idx] < 0) continue;
           double diff = scores[idx] - bleus[idx];
           value += diff*diff;
           double dprefix = 2*diff*scores[idx]*(1-scores[idx]);
           int[] fIndex = chl.getFIndices()[idx];
           float[] fValue = chl.getFValues()[idx];
           for (int fI = 0; fI < fIndex.length; fI++)
             derivative[fIndex[fI]] += dprefix * fValue[fI];
           derivative[biasWtIdx] += dprefix;
        }
      }
    }
  }

  public double getSSE() { double sse = 0;
    int mlen = 0;
    for (CompactHypothesisList chl : lchl) {
        int len = chl.getScores().length; if (len > mlen) mlen = len; }
    double[] bleus = new double[mlen];
    for (CompactHypothesisList chl : lchl) {
       double[] scores = getPredictedScores(chl);
       double[] origBleus = chl.getScores();
       for (int i = 0; i < origBleus.length; i++) bleus[i] = origBleus[i];
       if  (renormalize) if(!renormalizeScores(bleus,origBleus.length))continue;
       for (int idx = 0; idx < scores.length; idx++) {
         // temp hack for sentence bleu
         if (!renormalize && bleus[idx] == 0) continue;
         if (renormalize && bleus[idx] < 0) continue;
         double diff = scores[idx] - bleus[idx];
         sse += diff*diff;
       }
    }
    return sse;
  }

  // we output expected scores between 0 and 1.0
  //(e.g. sentence METEOR scores), not probabilities
  public boolean isLogLinear() { return false; }

  public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    if (System.getProperty(RENORMALIZE_PROP) != null)  renormalize = true;
    System.out.printf("Re-normalize: %s\n", renormalize);
    ObjF objF = new ObjF();
    wts = new double[featureIndex.size()+1]; // we need room for the bias wt
    biasWtIdx = featureIndex.size();
    Minimizer minim = new CGMinimizer(false);
    wts = minim.minimize(objF, 1e-4, wts);
    System.out.printf("Final SSE: %.3f\n", getSSE());
  }
}
