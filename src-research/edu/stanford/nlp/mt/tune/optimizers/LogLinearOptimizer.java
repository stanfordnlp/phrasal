package edu.stanford.nlp.mt.tune.optimizers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.StaticScorer;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.LinearCombinationMetric;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.tune.HillClimbingMultiTranslationMetricMax;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.OWLQNMinimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class LogLinearOptimizer extends AbstractNBestOptimizer {

  final double l2sigma;
  final double l1b;
  final int minFeatureCount;
  final Set<String> validFeature;
  
  static final int DEFAULT_MIN_FEATURE_COUNT = 0;
  static final double DEFAULT_L2_SIGMA = 100;
  static final double DEFAULT_L1_B = 0;
  
  
  @Override
  public boolean doNormalization() {
    return false;
  }

  public LogLinearOptimizer(MERT mert, String... fields) {
    super(mert);
    
    double l2sigma = DEFAULT_MIN_FEATURE_COUNT, l1b = DEFAULT_L1_B;
    int minFeatureCount = DEFAULT_MIN_FEATURE_COUNT;
    
    if (fields.length >= 1) {
      l2sigma = Double.parseDouble(fields[0]);      
    }
    if (fields.length >= 2) {
      l1b = Double.parseDouble(fields[1]);
    }
    if (fields.length >= 3) {
      minFeatureCount = Integer.parseInt(fields[2]);
    }
    
    System.err.println("Log-Linear training:");

    if (l2sigma == 0) {
      System.err.printf("   - No Gaussian prior / L2 regularization\n");
    } else {
      double truePenalty = l2sigma * l2sigma;
      System.err
          .printf(
              "   - Gaussian prior / L2 regularization with sigma: %.2f (penalty: %.2f)\n",
              l2sigma, truePenalty);
    }

    if (l1b == 0.0) {
      System.err.printf("   - No Laplace prior / L1 regularization\n");
    } else {
      int N = nbest.nbestLists().size();
      System.err.printf(
          "   - Laplace prior / L1 regularization with b: %.2f\n", l1b);
      System.err.printf("     OWLQNMinimizer C = N*1/b: %.2f\n", N * 1. / l1b);    
    }
    
    
    Counter<String> featureCounts = new ClassicCounter<String>();
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : MERT.nbest.nbestLists()) {
      Set<String> sentenceFeatures = new HashSet<String>();
      for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
          for (FeatureValue<String> fv: trans.features) {
            sentenceFeatures.add(fv.name);
          }
      }
      for (String feature : sentenceFeatures) {
        featureCounts.incrementCount(feature);
      }
    }
    validFeature = new HashSet<String>();
    for (String feature : featureCounts.keySet()) {
      if (featureCounts.getCount(feature) >= minFeatureCount) {
        validFeature.add(feature);
      }
    }
    
    System.err.printf("n-best list contains %d features\n", featureCounts.size());
    System.err.printf("Filtered to %d features for training ", validFeature.size());
    System.err.printf("since model features must occur in +%d segments\n", minFeatureCount);
    
    this.l2sigma = l2sigma;
    this.l1b = l1b;
    this.minFeatureCount = minFeatureCount;
    
  }

  @SuppressWarnings("unchecked")
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);

    EvaluationMetric<IString, String> modelMetric = new LinearCombinationMetric<IString, String>(
        new double[] { 1.0 },
        new ScorerWrapperEvaluationMetric<IString, String>(new StaticScorer(
            initialWts)));

    List<ScoredFeaturizedTranslation<IString, String>> current = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        modelMetric)).maximize(nbest);

    List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
        emetric)).maximize(nbest);

    System.err.println("Target model: " + modelMetric.score(target)
        + " metric: " + emetric.score(target));
    System.err.println("Current model: " + modelMetric.score(current)
        + " metric: " + emetric.score(current));
    Counter<String> currentCounts = new ClassicCounter<String>();
    for (ScoredFeaturizedTranslation<IString, String> t : current) {
      for (FeatureValue<String> feat : t.features) {
        currentCounts.incrementCount(feat.name, feat.value);
      }
    }

    Counter<String> targetCounts = new ClassicCounter<String>();

    for (ScoredFeaturizedTranslation<IString, String> t : target) {
      for (FeatureValue<String> feat : t.features) {
        targetCounts.incrementCount(feat.name, feat.value);
      }
    }
    System.err.println("Current LD "
        + currentCounts.getCount("LinearDistortion"));
    System.err
        .println("Target LD " + targetCounts.getCount("LinearDistortion"));
    System.err.printf("Target Features: %s...\n", Counters.toBiggestValuesFirstString(targetCounts, 100));
    // create a mapping between weight names and optimization
    // weight vector positions
    String[] weightNames = new String[validFeature.size()];
    System.err.printf("Model feautures: %s...\n", validFeature);
    
    double[] initialWtsArr = new double[validFeature.size()];
    
    int nameIdx = 0;
    for (String feature : validFeature) {
      initialWtsArr[nameIdx] = wts.getCount(feature);
      weightNames[nameIdx++] = feature;
    }

    System.err.println("Target Score: " + emetric.score(target));
    int N = nbest.nbestLists().size();
    Minimizer<DiffFunction> qn = l1b != 0.0 ? new OWLQNMinimizer(N * 1. / l1b)
        : new QNMinimizer(15, true);
    LogLinearObjective llo = new LogLinearObjective(weightNames, target);
    double initialValueAt = llo.valueAt(initialWtsArr);
    if (initialValueAt == Double.POSITIVE_INFINITY  || Double.isNaN(initialValueAt)) {
      System.err
          .printf("Initial Objective is infinite/NaN - normalizing weight vector");
      double normTerm = Counters.L2Norm(wts);
      for (int i = 0; i < initialWtsArr.length; i++) {
        initialWtsArr[i] /= normTerm;
      }
    }
    double initialObjValue = llo.valueAt(initialWtsArr);
    double initalDNorm = OptimizerUtils.norm2DoubleArray(llo
        .derivativeAt(initialWtsArr));
    double initalXNorm = OptimizerUtils.norm2DoubleArray(initialWtsArr);

    System.err.println("Initial Objective value: " + initialObjValue);
    System.err.println("l2 Original wts: " + Counters.L2Norm(wts));
    double newX[] = qn.minimize(llo, 1e-4, initialWtsArr); // new
                                                           // double[wts.size()]

    Counter<String> newWts = new ClassicCounter<String>();
    for (int i = 0; i < weightNames.length; i++) {
      newWts.setCount(weightNames[i], newX[i]);
    }

    double finalObjValue = llo.valueAt(newX);
    double finalDNorm = OptimizerUtils.norm2DoubleArray(llo.derivativeAt(newX));
    double finalXNorm = OptimizerUtils.norm2DoubleArray(newX);

    System.err.println("Final Objective value: " + finalObjValue);
    double metricEval = MERT.evalAtPoint(nbest, newWts, emetric);
    System.err.println("Final Eval at point: " + metricEval);
    System.err.println("l2 Final wts: " + Counters.L2Norm(newWts));
    double objDiff = initialObjValue - finalObjValue;
    System.err.println(">>>[Converge Info] ObjInit(" + initialObjValue
        + ") - ObjFinal(" + finalObjValue + ") = ObjDiff(" + objDiff
        + ") L2DInit(" + initalDNorm + ") L2DFinal(" + finalDNorm
        + ") L2XInit(" + initalXNorm + ") L2XFinal(" + finalXNorm + ")");
    MERT.updateBest(newWts, metricEval, true);
    return newWts;
  }

  class LogLinearObjective implements DiffFunction {
    final String[] weightNames;
    final List<ScoredFeaturizedTranslation<IString, String>> target;

    public LogLinearObjective(String[] weightNames,
        List<ScoredFeaturizedTranslation<IString, String>> target) {
      this.weightNames = weightNames;
      this.target = target;
    }

    private Counter<String> vectorToWeights(double[] x) {
      Counter<String> wts = new ClassicCounter<String>();
      for (int i = 0; i < weightNames.length; i++) {
        wts.setCount(weightNames[i], x[i]);
      }
      return wts;
    }

    private double[] counterToVector(Counter<String> c) {
      double[] v = new double[weightNames.length];
      for (int i = 0; i < weightNames.length; i++) {
        v[i] = c.getCount(weightNames[i]);
      }
      return v;
    }

    @Override
    public double[] derivativeAt(double[] x) {
    	System.err.println("DerivativeAt");        
    	for (int i = 0; i < weightNames.length; i++) {
    	  System.err.printf("%s: %e\n", weightNames[i], x[i]);
      }
      System.err.println();
      
      Counter<String> wts = vectorToWeights(x);
      Counter<String> dOplus = new ClassicCounter<String>();
      Counter<String> dOminus = new ClassicCounter<String>();
      Counter<String> dORegularize = new ClassicCounter<String>();

      Counter<String> dOdW = new ClassicCounter<String>();
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
          .nbestLists();
      for (int sentId = 0; sentId < nbestLists.size(); sentId++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(sentId);
        ScoredFeaturizedTranslation<IString, String> targetTrans = target
            .get(sentId);

        for (FeatureValue<String> fv : targetTrans.features) {
          dOplus.incrementCount(fv.name, fv.value);
        }

        double logZ = logZ(nbestList, wts);
        for (ScoredFeaturizedTranslation<IString, String> trans : nbestList) {
          // double p = Math.exp(scoreTranslation(wts, trans))/Z;
          double p = Math.exp(OptimizerUtils.scoreTranslation(wts, trans)
              - logZ);
          for (FeatureValue<String> fv : trans.features) {
            dOminus.incrementCount(fv.name, fv.value * p);
          }
        }
      }

      if (l2sigma != 0) {
        double N = nbestLists.size();
        for (String wt : wts.keySet()) {
          dORegularize.setCount(wt,
              N * (1. / (l2sigma * l2sigma)) * wts.getCount(wt));
        }
      }

      /*
       * System.err.println("dOPlus "+dOplus);
       * 
       * System.err.println("dOMinus "+dOminus);
       * 
       * System.err.println("dORegularize "+dORegularize);
       */

      dOdW.addAll(dOplus);
      Counters.subtractInPlace(dOdW, dOminus);
      Counters.multiplyInPlace(dOdW, -1);
      dOdW.addAll(dORegularize);
      System.err.println("wts(LinearDistortion): "
          + wts.getCount("LinearDistortion"));
      System.err.println("dOPlus(LinearDistortion): "
          + dOplus.getCount("LinearDistortion"));
      System.err.println("dOMinus(LinearDistortion): "
          + dOminus.getCount("LinearDistortion"));
      System.err.println("dOReg(LinearDistortion): "
          + dORegularize.getCount("LinearDistortion"));
      System.err.println("dOdW(LinearDistortion): "
          + dOdW.getCount("LinearDistortion"));
      return counterToVector(dOdW);
    }

    private double logZ(
        List<ScoredFeaturizedTranslation<IString, String>> translations,
        Counter<String> wts) {
      double scores[] = new double[translations.size()];
      int max_i = 0;

      Iterator<ScoredFeaturizedTranslation<IString, String>> iter = translations
          .iterator();
      for (int i = 0; iter.hasNext(); i++) {
        ScoredFeaturizedTranslation<IString, String> trans = iter.next();
        scores[i] = OptimizerUtils.scoreTranslation(wts, trans);
        if (scores[i] > scores[max_i])
          max_i = i;
      }

      double expSum = 0;
      for (int i = 0; i < scores.length; i++) {
        expSum += Math.exp(scores[i] - scores[max_i]);
      }

      return scores[max_i] + Math.log(expSum);
    }

    @Override
    public double valueAt(double[] x) {
      Counter<String> wts = vectorToWeights(x);
      // System.err.println("valueAt x[]: " + Arrays.toString(x));
      // System.err.println("wts: " + wts);
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
          .nbestLists();
      double sumLogP = 0;
      for (int sentId = 0; sentId < nbestLists.size(); sentId++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(sentId);
        ScoredFeaturizedTranslation<IString, String> targetTrans = target
            .get(sentId);

        double logP = OptimizerUtils.scoreTranslation(wts, targetTrans)
            - logZ(nbestList, wts);
        sumLogP += logP;
      }

      double regTerm = 0;
      if (l2sigma != 0) {
        double N = nbestLists.size();
        for (double w : x) {
          regTerm += N * (1. / (2. * l2sigma * l2sigma)) * w * w;
        }
      }

      double regularizeObjective = -sumLogP + regTerm;
      System.err.printf("sumLogP: %.5f Eval at point: %.5f\n", sumLogP,
          MERT.evalAtPoint(nbest, wts, emetric));
      double C = l2sigma * l2sigma;
      System.err.printf(
          "regTerm(sigma: %.5f C: %.5f): %.5f regularized objective: %.5f\n",
          l2sigma, C, regTerm, regularizeObjective);

      return regularizeObjective;
    }

    @Override
    public int domainDimension() {
      return weightNames.length;
    }
  }
}