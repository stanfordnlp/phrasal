package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;
import java.util.TreeSet;

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
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class LogLinearMultiTarget extends AbstractNBestOptimizer {
  final double l2sigma;
  final double topFrac;
  
  @Override
  public boolean doNormalization() {
    return false;
  }
  
  @Override 
  public boolean selfWeightUpdate() {
    return true;
  }

  public LogLinearMultiTarget(MERT mert, String... args) {
    super(mert);
    double l2sigma = 10.0;
    double topFrac = 0.05;
    
    if (args.length >= 1) {
      topFrac = Double.parseDouble(args[0]);
    }
    
    if (args.length >= 2) {
      l2sigma = Double.parseDouble(args[1]);      
    }
    
    System.err.println("Log-linear multi-target training:");

    if (l2sigma == 0) {
      System.err.printf("   - No Gaussian prior / L2 regularization\n");
    } else {
      double truePenalty = l2sigma * l2sigma;
      System.err
          .printf(
              "   - Gaussian prior / L2 regularization with sigma: %.2f (penalty: %.2f)\n",
              l2sigma, truePenalty);
    }
    this.l2sigma = l2sigma;
    this.topFrac = topFrac;
  }

  @SuppressWarnings("unchecked")
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

    // create a mapping between weight names and optimization
    // weight vector positions
    String[] weightNames = new String[wts.size()];
    double[] initialWtsArr = new double[wts.size()];

    int nameIdx = 0;
    for (String feature : new TreeSet<String>(wts.keySet())) {
      initialWtsArr[nameIdx] = wts.getCount(feature);
      weightNames[nameIdx++] = feature;
    }

    double[][] lossMatrix = OptimizerUtils.calcDeltaMetric(nbest, target,
        emetric);

    Minimizer<DiffFunction> qn = new QNMinimizer(15, true);
    LogLinearMultiTargetObj llmt = new LogLinearMultiTargetObj(
        weightNames, lossMatrix);
    double initialValueAt = llmt.valueAt(initialWtsArr);
    if (initialValueAt == Double.POSITIVE_INFINITY
        || initialValueAt != initialValueAt) {
      System.err
          .printf("Initial Objective is infinite/NaN - normalizing weight vector");
      double normTerm = Counters.L2Norm(wts);
      for (int i = 0; i < initialWtsArr.length; i++) {
        initialWtsArr[i] /= normTerm;
      }
    }
    double initialObjValue = llmt.valueAt(initialWtsArr);
    double initalDNorm = OptimizerUtils.norm2DoubleArray(llmt
        .derivativeAt(initialWtsArr));
    double initalXNorm = OptimizerUtils.norm2DoubleArray(initialWtsArr);

    System.err.println("Initial Objective value: " + initialObjValue);
    double newX[] = qn.minimize(llmt, 1e-4, initialWtsArr); // new
                                                            // double[wts.size()]
    Counter<String> newWts = OptimizerUtils.getWeightCounterFromArray(
        weightNames, newX);
    double finalObjValue = llmt.valueAt(newX);

    double objDiff = initialObjValue - finalObjValue;
    double finalDNorm = OptimizerUtils
        .norm2DoubleArray(llmt.derivativeAt(newX));
    double finalXNorm = OptimizerUtils.norm2DoubleArray(newX);
    double metricEval = MERT.evalAtPoint(nbest, newWts, emetric);
    System.err.println(">>>[Run Info] ObjInit(" + initialObjValue
        + ") - ObjFinal(" + finalObjValue + ") = ObjDiff(" + objDiff
        + ") L2DInit(" + initalDNorm + ") L2DFinal(" + finalDNorm
        + ") L2XInit(" + initalXNorm + ") L2XFinal(" + finalXNorm + ")");

    
    synchronized (MERT.bestWts) {
      double[] bestWts = OptimizerUtils.getWeightArrayFromCounter(weightNames, MERT.bestWts);
      double bestWtsObj = llmt.valueAt(bestWts);
      if (finalObjValue < bestWtsObj) {
        System.err.println("\nNew best obj: "+finalObjValue+"(Eval: "+metricEval+") old best obj: "+bestWtsObj + " size wt: " + newX.length);
        System.err.println(">>>[Converge Info] ObjInit(" + initialObjValue
            + ") - ObjFinal(" + finalObjValue + ") = ObjDiff(" + objDiff
            + ") L2DInit(" + initalDNorm + ") L2DFinal(" + finalDNorm
            + ") L2XInit(" + initalXNorm + ") L2XFinal(" + finalXNorm + ")");
        MERT.updateBest(newWts, metricEval, true);
        double[] newBestWts = OptimizerUtils.getWeightArrayFromCounter(weightNames, MERT.bestWts);
        double newBestWtsObj = llmt.valueAt(newBestWts);
        System.err.println("New best wts obj: "+newBestWtsObj);
      }
    }
    
    return newWts;
  }

  class LogLinearMultiTargetObj implements DiffFunction {
    final String[] weightNames;   
    final double[][] lossMatrix;
    final int[][] sortIndices;

    public LogLinearMultiTargetObj(String[] weightNames,
        double[][] lossMatrix) {
      this.weightNames = weightNames;
      this.lossMatrix = lossMatrix;
      this.sortIndices = OptimizerUtils.deltaMetricToSortedIndicies(lossMatrix);
      for (int i = 0; i < 10; i++) {
        for (int j = 0; j < sortIndices[i].length; j++) {
          System.err.printf("%d.%d(%d): %f\n", i, j, sortIndices[i][j], lossMatrix[i][sortIndices[i][j]]);
        }
      }
    }
     

    @Override
    public double valueAt(double[] wtsArr) {
      Counter<String> wts = OptimizerUtils.getWeightCounterFromArray(
          weightNames, wtsArr);
      
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
      .nbestLists();
      
      double regTerm = 0;
      if (l2sigma != 0) {
        double N = nbestLists.size();
        for (double w : wtsArr) {
          regTerm += N * (1. / (2. * l2sigma * l2sigma)) * w * w;
        }
      }

      double sumLogP = 0;
     
      for (int i = 0; i < nbestLists.size(); i++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(i);
        double[] scores = OptimizerUtils.scoreTranslations(wts, nbestList);
        double[] targetScores = new double[(int)(lossMatrix[i].length*topFrac)];
        if (targetScores.length == 0) {
          targetScores = new double[1]; // make sure there is at least one target
        }
        for (int j = 0; j < targetScores.length; j++) {
          targetScores[j] = scores[sortIndices[i][j]];
        }
        /*
        System.err.println("Target scores: " + Arrays.toString(targetScores));
        System.err.println("Scores: " + Arrays.toString(scores)); */
        
        double targetSoftMax = OptimizerUtils
        .softMaxFromDoubleArray(targetScores);
        
        double Z = OptimizerUtils
            .softMaxFromDoubleArray(scores);
        
        //System.err.println("Target softmax(len "+targetScores.length+"): "+targetSoftMax);
        //System.err.println("All softmax(len "+scores.length+"): "+Z);
        sumLogP += targetSoftMax - Z;
      }

      double regularizedObjective = -sumLogP + regTerm;
      return regularizedObjective;
    }

    @Override
    public int domainDimension() {
      return weightNames.length;
    }

    @Override
    public double[] derivativeAt(double[] wtsArr) {
      Counter<String> wts = OptimizerUtils.getWeightCounterFromArray(
          weightNames, wtsArr);
      Counter<String> dORegularize = new ClassicCounter<String>();
      Counter<String> dOplus = new ClassicCounter<String>();
      Counter<String> dOminus = new ClassicCounter<String>();
      Counter<String> dOdW = new ClassicCounter<String>();
      
      List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
          .nbestLists();

      if (l2sigma != 0) {
        double N = nbestLists.size();
        for (String wt : wts.keySet()) {
          dORegularize.setCount(wt,
              N * (1. / (l2sigma * l2sigma)) * wts.getCount(wt));
        }
      }
      
      for (int sentId = 0; sentId < nbestLists.size(); sentId++) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists
            .get(sentId);
      
        double[] scores = OptimizerUtils.scoreTranslations(wts, nbestList);
        double[] targetScores = new double[(int)(lossMatrix[sentId].length*topFrac)];
        if (targetScores.length == 0) {
          targetScores = new double[1]; // make sure there is at least one target
        }
        for (int j = 0; j < targetScores.length; j++) {
          targetScores[j] = scores[sortIndices[sentId][j]];
        }
        double targetLogZ = OptimizerUtils.softMaxFromDoubleArray(targetScores);              
        double logZ = OptimizerUtils.softMaxFromDoubleArray(scores);
        
        for (int j = 0; j < targetScores.length; j++) {
           double p = Math.exp(targetScores[j] - targetLogZ);
           for (FeatureValue<String> fv : nbestList.get(sortIndices[sentId][j]).features) {
             dOplus.incrementCount(fv.name, fv.value * p);
           }
        }
        
        for (int j = 0; j < scores.length; j++) {          
          double p = Math.exp(scores[j]-logZ);
          for (FeatureValue<String> fv : nbestList.get(j).features) {
            dOminus.incrementCount(fv.name, fv.value * p);
          }
        }
      }

      dOdW.addAll(dOplus);
      Counters.subtractInPlace(dOdW, dOminus);
      Counters.multiplyInPlace(dOdW, -1);
      dOdW.addAll(dORegularize);
            
      return OptimizerUtils.getWeightArrayFromCounter(weightNames, dOdW);
    }
  }
}