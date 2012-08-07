package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhrasalUtil;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.StaticScorer;
import edu.stanford.nlp.mt.tune.optimizers.OptimizerUtils;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.GDMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

/**
 * Entropy Adaptation tool
 *
 * @author danielcer
 *
 */
public class EntropyAdaptation {
  public static final boolean VERY_VERBOSE = false;
  public static final boolean VERBOSE = false;
  public static final int LBFGS_VECTORS = 15;
  public static final double TOL = 1.0e-12;
  public static final int RANDOM_STARTING_POINTS = 20;
  public static final double[] lrate_schedule = {0.1, 0.01};
  
  public static void main(String[] argv) throws Exception { 
    if (argv.length != 3 && argv.length != 4) { 
      err.println(
          "Usage:\n\tjava ...MinimumBayesRisk (n-best list) (weights) (C) [new weights]");
      exit(-1);      
    }
    String nbestfn = argv[0];
    String weightsfn = argv[1];
    double C = Double.parseDouble(argv[2]);
    String newWeightsFn = (argv.length > 3 ? argv[3] : null);
    
    Counter<String> weights = PhrasalUtil.readWeights(weightsfn);
    String[] weightNames = OptimizerUtils.getWeightNamesFromCounter(weights);
    
    FlatNBestList nbestlists = new FlatNBestList(nbestfn);
    GDMinimizer gd = new GDMinimizer(null);
    EntropyObjective pobj = new EntropyObjective(nbestlists, weights, C);
    Random r = new Random(1);
    
    double bestObj = Double.POSITIVE_INFINITY;
    double[] bestWeights = null;

    for (int pt = 0; pt < RANDOM_STARTING_POINTS; pt++) {
      double[] initialWts = new double[weights.size()];
      
      if (pt > 0) {
        for (int i = 0; i < initialWts.length; i++) {
          initialWts[i] = 0.5-r.nextDouble();
        }
      } else {
        initialWts = OptimizerUtils.getWeightArrayFromCounter(weightNames, weights);
      }
      
      double initialObj = pobj.valueAt(initialWts);
      double initialEntropy = pobj.computeEntropy(initialWts);
      err.printf("Point %d\n", pt);
      err.printf("\tInital Obj: %e\n", initialObj);
      err.printf("\tInital Entropy: %e\n", initialEntropy);
      double[] optWeights = initialWts;
      for (double lrate : lrate_schedule) {
        gd.setStepSize(lrate/nbestlists.nbestLists().size());
        optWeights = gd.minimize(pobj, TOL, optWeights);
        double obj = pobj.valueAt(optWeights);
        double entropy = pobj.computeEntropy(optWeights);
        err.printf("\tlrate: %e obj: %e entropy: %e\n", lrate, obj, entropy);
      }
      double finalObj = pobj.valueAt(optWeights);
      double finalEntropy = pobj.computeEntropy(optWeights);
      
      err.printf("Point %d\n", pt);
      err.printf("\tInital Obj: %e Final Obj: %e\n", initialObj, finalObj);
      err.printf("\tInital Entropy: %e Final Entropy: %e\n", initialEntropy, finalEntropy);
      if (finalObj < bestObj) {
        err.printf("New best [pt %d] Obj: %e\n", pt, finalObj);
        bestObj = finalObj;
        bestWeights = optWeights;
      }
    }
    
    Counter<String> newWeights = OptimizerUtils.getWeightCounterFromArray(weightNames, bestWeights);
    StaticScorer scorer = new StaticScorer(newWeights);
    
    if (newWeightsFn != null) {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(newWeightsFn));
      oos.writeObject(newWeights);
      oos.close();
    }
    
    // print out re-ranked n-best list
    int idx = -1; 
    for (List<ScoredFeaturizedTranslation<IString,String>> nbestlist :
       nbestlists.nbestLists()) { idx++;
       List<Pair<Double,ScoredFeaturizedTranslation<IString,String>>> 
       rescoredNBestList = new ArrayList<Pair<Double,ScoredFeaturizedTranslation<IString,String>>>(nbestlist.size());
 
       for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
         double score = scorer.getIncrementalScore(trans.features);
            rescoredNBestList.add(new Pair<Double,ScoredFeaturizedTranslation<IString,String>>(score, trans));
       }
       Collections.sort(rescoredNBestList);
       Collections.reverse(rescoredNBestList);
       for (Pair<Double,ScoredFeaturizedTranslation<IString,String>> entry : rescoredNBestList) {
         out.printf("%d ||| %s ||| %e\n", idx, 
             entry.second().translation, entry.first());
       }
    }
  }

}

class EntropyObjective implements DiffFunction {
  final FlatNBestList nbestlists;
  final double C;
  final Counter<String> trainedWeights;
  final String[] weightNames;
  final int datapts;
  public EntropyObjective(FlatNBestList nbestlists, Counter<String> weights, double C) {
    this.nbestlists = nbestlists;
    this.C = C;
    this.trainedWeights = weights;
    this.weightNames = OptimizerUtils.getWeightNamesFromCounter(weights);
    this.datapts = nbestlists.nbestLists().size();
  }
  
  public double computeEntropy(double[] x) {
    return computeEntropy(OptimizerUtils.getWeightCounterFromArray(weightNames, x));
  }
  
  public double computeEntropy(Counter<String> weights) {
    StaticScorer scorer = new StaticScorer(weights);
    double sumEntropy = 0;
    for (List<ScoredFeaturizedTranslation<IString,String>> nbestlist :
      nbestlists.nbestLists()) {
      double[] scores = new double[nbestlist.size()];
      int scoreI = 0;
      for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
         scores[scoreI++] = scorer.getIncrementalScore(trans.features);
      }
      double logZ = OptimizerUtils.softMaxFromDoubleArray(scores);
      
      double localEntropy = 0;
      for (int i = 0; i < scores.length; i++) {
        // p log p
        double plogp = Math.exp(scores[i] - logZ) * (scores[i] - logZ);
        localEntropy -= plogp;  
      }
      sumEntropy += localEntropy;
    }
    return sumEntropy;  
  }
  
  @Override
  public double valueAt(double[] x) {
    Counter<String> weights = OptimizerUtils.getWeightCounterFromArray(weightNames, x);
    
    double sumEntropy = computeEntropy(weights);
    if (EntropyAdaptation.VERY_VERBOSE) {
      System.err.printf("Weights:\n%s\n", weights);
      System.err.printf("SumEntropy: %e\n", sumEntropy);
    }
    Counter<String> weightDiff = new ClassicCounter<String>(trainedWeights);
    Counters.subtractInPlace(weightDiff, weights);
    double weightDiffL2 = Counters.L2Norm(weightDiff);
    double quadraticPenalty = weightDiffL2*weightDiffL2;
    double obj = (sumEntropy/datapts) + C * quadraticPenalty;
    if (EntropyAdaptation.VERY_VERBOSE) {
      System.err.printf("||w_trn-w||_2^2: %e\n", quadraticPenalty);
      System.err.printf("Objective: %e\n", obj);
    }
    return obj;
  }

  @Override
  public int domainDimension() {
    return weightNames.length;
  }

  @Override
  public double[] derivativeAt(double[] x) {
    Counter<String> weights = OptimizerUtils.getWeightCounterFromArray(weightNames, x);
    StaticScorer scorer = new StaticScorer(weights);
    
    Counter<String> dOdw = new ClassicCounter<String>();
    for (String weightName : weights.keySet()) {
      dOdw.incrementCount(weightName, 2*C*(weights.getCount(weightName) - trainedWeights.getCount(weightName)));
    }
    
  
    Counter<String> E_E_F = new ClassicCounter<String>();
    Counter<String> E_F = new ClassicCounter<String>();
    double entropy = 0;
  
    for (List<ScoredFeaturizedTranslation<IString,String>> nbestlist :
      nbestlists.nbestLists()) {
      double[] scores = new double[nbestlist.size()];
      int scoreI = 0;
      for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
         scores[scoreI++] = scorer.getIncrementalScore(trans.features);
      }
      double logZ = OptimizerUtils.softMaxFromDoubleArray(scores);
      double localEntropy = 0;
      Counter<String> localE_E_F = new ClassicCounter<String>();
      Counter<String> localE_F = new ClassicCounter<String>();
      
      for (int i = 0; i < scores.length; i++) {
         for (FeatureValue<String> feat : nbestlist.get(i).features) {
           localE_F.incrementCount(feat.name,   feat.value * Math.exp(scores[i]-logZ));
           localE_E_F.incrementCount(feat.name, feat.value * Math.exp(scores[i]-logZ)*(scores[i] - logZ));
         }
         localEntropy += Math.exp(scores[i]-logZ)*(scores[i] - logZ);
      }

      entropy += localEntropy; // /Math.exp(logZ);
      E_F.addAll(localE_F);
      E_E_F.addAll(localE_E_F);
    }
    
    Counter<String> dEdw = new ClassicCounter<String>();
    
    for (String weightName : weightNames) {
      dEdw.incrementCount(weightName, E_E_F.getCount(weightName));
      dEdw.decrementCount(weightName, E_F.getCount(weightName)*entropy);
      if (EntropyAdaptation.VERY_VERBOSE) {
        System.err.printf("E_E_F[%s]: %e\n", weightName, E_E_F.getCount(weightName));
        System.err.printf("E_F[%s]: %e\n", weightName, E_F.getCount(weightName)*entropy);
      }
    }

    Counters.multiplyInPlace(dEdw, -1.0);
    Counters.divideInPlace(dEdw, datapts);
    dOdw.addAll(dEdw);
    if (EntropyAdaptation.VERY_VERBOSE) {
      System.err.printf("dEdW:\n%s\n", dEdw);
    }
    return OptimizerUtils.getWeightArrayFromCounter(weightNames, dOdw);
  }
  
}