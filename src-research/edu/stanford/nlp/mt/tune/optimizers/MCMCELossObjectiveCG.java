package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.HasInitial;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.MutableDouble;

/**
 * @author danielcer
 */
public class MCMCELossObjectiveCG extends AbstractBatchOptimizer {

  public MCMCELossObjectiveCG(MERT mert) {
    super(mert);
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    double C = MERT.C;

    Counter<String> sgdWts;
    System.err.println("Begin SGD optimization\n");
    sgdWts = new MCMCELossObjectiveSGD(mert, 50).optimize(initialWts);
    double eval = MERT.evalAtPoint(nbest, sgdWts, emetric);
    double regE = mert.mcmcTightExpectedEval(nbest, sgdWts, emetric);
    double l2wtsSqred = Counters.L2Norm(sgdWts);
    l2wtsSqred *= l2wtsSqred;
    System.err.printf("SGD final reg objective 0.5||w||_2^2 - C*E(Eval): %e\n",
        -regE);
    System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
    System.err.printf("E(Eval): %e\n", (regE + 0.5 * l2wtsSqred) / C);
    System.err.printf("C: %e\n", C);
    System.err.printf("Last eval: %e\n", eval);

    System.err.println("Begin CG optimization\n");
    ObjELossDiffFunction obj = new ObjELossDiffFunction(mert, sgdWts);
    // CGMinimizer minim = new CGMinimizer(obj);
    QNMinimizer minim = new QNMinimizer(obj, 10, true);

    // double[] wtsDense = minim.minimize(obj, 1e-5, obj.initial);
    // Counter<String> wts = new ClassicCounter<String>();
    // for (int i = 0; i < wtsDense.length; i++) {
    // wts.incrementCount(obj.featureIdsToString.get(i), wtsDense[i]);
    // }
    while (true) {
      try {
        minim.minimize(obj, 1e-5, obj.initial);
        break;
      } catch (Exception e) {
        // continue;
      }
    }
    Counter<String> wts = obj.getBestWts();

    eval = MERT.evalAtPoint(nbest, wts, emetric);
    regE = mert.mcmcTightExpectedEval(nbest, wts, emetric);
    System.err.printf("CG final reg 0.5||w||_2^2 - C*E(Eval): %e\n", -regE);
    l2wtsSqred = Counters.L2Norm(wts);
    l2wtsSqred *= l2wtsSqred;
    System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
    System.err.printf("E(Eval): %e\n", (regE + 0.5 * l2wtsSqred) / C);
    System.err.printf("C: %e\n", C);
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }

  static class ObjELossDiffFunction implements DiffFunction, HasInitial {

    final MERT mert;
    final FlatNBestList nbest;
    final Counter<String> initialWts;
    final EvaluationMetric<IString, String> emetric;
    final int domainDimension;

    final List<String> featureIdsToString;
    final double[] initial;
    final double[] derivative;

    public ObjELossDiffFunction(MERT mert, Counter<String> initialWts) {
      this.mert = mert;
      this.nbest = MERT.nbest;
      this.initialWts = initialWts;
      this.emetric = mert.emetric;
      Set<String> featureSet = new HashSet<String>();
      featureIdsToString = new ArrayList<String>();
      List<Double> initialFeaturesVector = new ArrayList<Double>();

      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
          .nbestLists()) {
        for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
          for (FeatureValue<String> featureValue : trans.features) {
            if (!featureSet.contains(featureValue.name)) {
              featureSet.add(featureValue.name);
              featureIdsToString.add(featureValue.name);
              initialFeaturesVector.add(initialWts.getCount(featureValue.name));
            }
          }
        }
      }

      initial = new double[initialFeaturesVector.size()];
      derivative = new double[initialFeaturesVector.size()];
      for (int i = 0; i < initial.length; i++) {
        initial[i] = initialFeaturesVector.get(i);
      }
      domainDimension = featureSet.size();
    }

    @Override
    public int domainDimension() {
      return domainDimension;
    }

    @Override
    public double[] initial() {
      return initial;
    }

    @Override
    public double[] derivativeAt(double[] wtsDense) {

      Counter<String> wtsCounter = new ClassicCounter<String>();
      for (int i = 0; i < wtsDense.length; i++) {
        wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
      }

      MutableDouble expectedEval = new MutableDouble();
      Counter<String> dE = new MCMCDerivative(mert, expectedEval)
          .optimize(wtsCounter);

      for (int i = 0; i < derivative.length; i++) {
        derivative[i] = dE.getCount(featureIdsToString.get(i));
      }
      return derivative;
    }

    public Counter<String> getBestWts() {
      Counter<String> wtsCounter = new ClassicCounter<String>();
      for (int i = 0; i < bestWts.length; i++) {
        wtsCounter.incrementCount(featureIdsToString.get(i), bestWts[i]);
      }
      return wtsCounter;
    }

    @Override
    public double valueAt(double[] wtsDense) {
      Counter<String> wtsCounter = new ClassicCounter<String>();

      for (int i = 0; i < wtsDense.length; i++) {
        if (wtsDense[i] != wtsDense[i])
          throw new RuntimeException("Weights contain NaN");
        wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
      }
      double eval = mert.mcmcTightExpectedEval(nbest, wtsCounter, emetric);
      if (eval < bestEval) {
        bestWts = wtsDense.clone();
      }
      return mert.mcmcTightExpectedEval(nbest, wtsCounter, emetric);
    }

    double bestEval = Double.POSITIVE_INFINITY;
    double[] bestWts;
  }
}