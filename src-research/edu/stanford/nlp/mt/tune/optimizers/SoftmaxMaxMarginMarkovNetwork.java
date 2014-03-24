package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.LinearCombinationMetric;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.tune.HillClimbingMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class SoftmaxMaxMarginMarkovNetwork extends AbstractBatchOptimizer {
  final double C;

  @Override
  public boolean doNormalization() {
    return false;
  }

  public SoftmaxMaxMarginMarkovNetwork(MERT mert, String... args) {
    super(mert);
    double C = 10;
    if (args.length == 1) {
      C = Double.parseDouble(args[0]);
    }
    System.err.printf("Softmax Max Margin Markov Network C=%.2f", C);
    this.C = C;
  }

  @SuppressWarnings("unchecked")
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);

    EvaluationMetric<IString, String> modelMetric = new LinearCombinationMetric<IString, String>(
        new double[] { 1.0 },
        new ScorerWrapperEvaluationMetric<IString, String>(new DenseScorer(
            initialWts)));

    List<ScoredFeaturizedTranslation<IString, String>> current = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        modelMetric)).maximize(nbest);

    List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        emetric)).maximize(nbest);

    System.err.println("Target model: " + modelMetric.score(target)
        + " metric: " + emetric.score(target));
    System.err.println("Current model: " + modelMetric.score(current)
        + " metric: " + emetric.score(current));

    // create a mapping between weight names and optimization
    // weight vector positions
    String[] weightNames = new String[wts.size()];
    double[] initialWtsArr = new double[wts.size()];

    int nameIdx = 0;
    for (String feature : wts.keySet()) {
      initialWtsArr[nameIdx] = wts.getCount(feature);
      weightNames[nameIdx++] = feature;
    }

    double[][] lossMatrix = OptimizerUtils.calcDeltaMetric(nbest, target,
        emetric);

    Minimizer<DiffFunction> qn = new QNMinimizer(15, true);
    SoftMaxMarginMarkovNetwork sm3n = new SoftMaxMarginMarkovNetwork(
        weightNames, target, lossMatrix);
    double initialValueAt = sm3n.valueAt(initialWtsArr);
    if (initialValueAt == Double.POSITIVE_INFINITY
        || initialValueAt != initialValueAt) {
      System.err
          .printf("Initial Objective is infinite/NaN - normalizing weight vector");
      double normTerm = Counters.L2Norm(wts);
      for (int i = 0; i < initialWtsArr.length; i++) {
        initialWtsArr[i] /= normTerm;
      }
    }
    double initialObjValue = sm3n.valueAt(initialWtsArr);
    double initalDNorm = OptimizerUtils.norm2DoubleArray(sm3n
        .derivativeAt(initialWtsArr));
    double initalXNorm = OptimizerUtils.norm2DoubleArray(initialWtsArr);

    System.err.println("Initial Objective value: " + initialObjValue);
    double newX[] = qn.minimize(sm3n, 1e-4, initialWtsArr); // new
                                                            // double[wts.size()]
    Counter<String> newWts = OptimizerUtils.getWeightCounterFromArray(
        weightNames, newX);
    double finalObjValue = sm3n.valueAt(newX);

    double objDiff = initialObjValue - finalObjValue;
    double finalDNorm = OptimizerUtils
        .norm2DoubleArray(sm3n.derivativeAt(newX));
    double finalXNorm = OptimizerUtils.norm2DoubleArray(newX);
    double metricEval = MERT.evalAtPoint(nbest, newWts, emetric);
    System.err.println(">>>[Converge Info] ObjInit(" + initialObjValue
        + ") - ObjFinal(" + finalObjValue + ") = ObjDiff(" + objDiff
        + ") L2DInit(" + initalDNorm + ") L2DFinal(" + finalDNorm
        + ") L2XInit(" + initalXNorm + ") L2XFinal(" + finalXNorm + ")");

    MERT.updateBest(newWts, metricEval, true);

    return newWts;
  }

  class SoftMaxMarginMarkovNetwork implements DiffFunction {
    final String[] weightNames;
    final List<ScoredFeaturizedTranslation<IString, String>> target;
    final double[][] lossMatrix;

    public SoftMaxMarginMarkovNetwork(String[] weightNames,
        List<ScoredFeaturizedTranslation<IString, String>> target,
        double[][] lossMatrix) {
      this.weightNames = weightNames;
      this.target = target;
      this.lossMatrix = lossMatrix;
    }

    @Override
    public double valueAt(double[] wtsArr) {
      Counter<String> wts = OptimizerUtils.getWeightCounterFromArray(
          weightNames, wtsArr);

      double sqrNormWts = OptimizerUtils.sumSquareDoubleArray(wtsArr);

      double sumErrorTerm = 0;
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
          .nbestLists();
      for (int i = 0; i < nbestLists.size(); i++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(i);
        double[] scores = OptimizerUtils.scoreTranslations(wts, nbestList);
        double targetScore = OptimizerUtils
            .scoreTranslation(wts, target.get(i));
        double[] lossAugmentedScores = new double[scores.length];
        for (int j = 0; j < scores.length; j++) {
          lossAugmentedScores[j] = scores[j] + lossMatrix[i][j];
        }
        double errorTerm = OptimizerUtils
            .softMaxFromDoubleArray(lossAugmentedScores) - targetScore;
        sumErrorTerm += errorTerm;
      }

      return 0.5 * sqrNormWts + C * sumErrorTerm;
    }

    @Override
    public int domainDimension() {
      return weightNames.length;
    }

    @Override
    public double[] derivativeAt(double[] wtsArr) {
      Counter<String> wts = OptimizerUtils.getWeightCounterFromArray(
          weightNames, wtsArr);
      Counter<String> dOdW = new ClassicCounter<String>();
      for (String wt : wts.keySet()) {
        dOdW.incrementCount(wt, wts.getCount(wt));
      }
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
          .nbestLists();

      for (int i = 0; i < target.size(); i++) {
        for (FeatureValue<String> fv : target.get(i).features) {
          dOdW.incrementCount(fv.name, -C * fv.value);
        }
      }

      for (int i = 0; i < nbestLists.size(); i++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(i);
        double[] scores = OptimizerUtils.scoreTranslations(wts, nbestList);
        double[] lossAugmentedScores = new double[scores.length];
        for (int j = 0; j < scores.length; j++) {
          lossAugmentedScores[j] = scores[j] + lossMatrix[i][j];
        }

        double Z = OptimizerUtils.softMaxFromDoubleArray(lossAugmentedScores);
        for (int j = 0; j < nbestList.size(); j++) {
          double p = Math.exp(lossAugmentedScores[j] - Z);
          for (FeatureValue<String> fv : nbestList.get(j).features) {
            dOdW.incrementCount(fv.name, C * (p * fv.value));
          }
        }
      }

      return OptimizerUtils.getWeightArrayFromCounter(weightNames, dOdW);
    }

  }
}