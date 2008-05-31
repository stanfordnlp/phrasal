package mt.reranker;

import java.util.List;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;

/** 
 * LogLinear Probabilistic Wrapper 
 *
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class LogLinearProbabilisticWrapper extends 
  AbstractOneOfManyClassifier { 

  AbstractOneOfManyClassifier cl;

  public LogLinearProbabilisticWrapper(AbstractOneOfManyClassifier cl) {
     this.cl = cl;
  }

  class ObjF extends AbstractCachingDiffFunction {
    public int domainDimension() { return 1; }

    public void calculate(double[] testWts) {
      wts = testWts;
      double sigma2 = 100;

      value = - (wts[0]*wts[0])/(2*sigma2);
      value *= lchl.size(); 

      double tV = 0, eV = 0;
      for (CompactHypothesisList chl : lchl) { double[] probs = getProbs(chl);
        double[] underLyingScores = cl.getAllScores(chl);
        value += Math.log(probs[0]);
        tV += underLyingScores[0]; // True value

        for (int j = 0; j < probs.length; j++) { // Expected values
          eV += probs[j]*underLyingScores[j]; }
      }

      derivative[0] = tV - eV - lchl.size()*wts[0]/sigma2;
      derivative[0] *= -1.0;
      value *= -1.0;
    }
  }

  public boolean isLogLinear() { return true; }

  // Here, linear scores are calculated by reweighting
  // the score assigned by the underlying classifier (cl)
  double[] getAllScores(CompactHypothesisList chl) { 
    double[] scores = cl.getAllScores(chl);
    for (int i = 0; i < scores.length; i++) scores[i] *= wts[0];
    return scores;
  }
 
  public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl); 
    wts = new double[1]; // we only have one feature: classifier score
    // Create a featureIndex so that various superclass methods
    // still play nice with us (e.g. displayWeights())
    featureIndex = new FeatureIndex();
    featureIndex.add(cl.getClass().getName()+"_score");
    ObjF objF = new ObjF();
    Minimizer<DiffFunction> minim = new QNMinimizer(objF);
    wts = minim.minimize(objF, 1e-4, wts);
  }
}
