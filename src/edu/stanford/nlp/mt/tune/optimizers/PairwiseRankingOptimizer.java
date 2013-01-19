package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.EvaluationMetricFactory;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * Pairwise Ranking Optimization
 * 
 * from Mark Hopkins and Jonathan May's 2011 EMNLP paper Tuning as Ranking 
 * 
 * @author daniel cer
 *
 */
public class PairwiseRankingOptimizer extends AbstractNBestOptimizer {
  static public final int DEFAULT_GAMMA = 5000;
  static public final int DEFAULT_XI = 50;
  static public final double DEFAULT_N_THRESHOLD = 0.05;
  static public final double DEFAULT_L2SIGMA = 0.1;
  static public final int DEFAULT_MIN_FEATURE_SEGMENT_COUNT = 3;
 
  static private boolean updatedBestOnce = false;
  
  final int gamma;
  final int xi;
  final double nThreshold;
  final double l2sigma;
  final int minFeatureSegmentCount;
  
  public PairwiseRankingOptimizer(MERT mert, double l2sigma, int minFeatureSegmentCount, int gamma, int xi, double nThreshold) {
    super(mert);
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.l2sigma = l2sigma;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
  }
  
  public PairwiseRankingOptimizer(MERT mert) {
    this(mert, DEFAULT_L2SIGMA, DEFAULT_MIN_FEATURE_SEGMENT_COUNT, DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD);
  }
  
  public PairwiseRankingOptimizer(MERT mert, String... fields) {
    super(mert);
    if (fields.length >= 1) {
      l2sigma = Double.parseDouble(fields[0]);
    } else {
      l2sigma = DEFAULT_L2SIGMA;
    }
    
    if (fields.length >= 2) {
      minFeatureSegmentCount = Integer.parseInt(fields[1]);
    } else {
      minFeatureSegmentCount = DEFAULT_MIN_FEATURE_SEGMENT_COUNT;
    }
    
    if (fields.length >= 3) {
      this.gamma = Integer.parseInt(fields[2]);
    } else {
      this.gamma = DEFAULT_GAMMA;
    }
    if (fields.length >= 4) {
      this.xi = Integer.parseInt(fields[3]);
    } else {
      this.xi = DEFAULT_XI;
    }
    if (fields.length >= 5) {
      this.nThreshold = Double.parseDouble(fields[4]);
    } else {
      this.nThreshold = DEFAULT_N_THRESHOLD;
    }
    
    System.err.println("PRO Parameters:");
    System.err.printf("\tl2sigma: %e\n", l2sigma);
    System.err.printf("\tminFeatureSegmentCount: %d\n", minFeatureSegmentCount);
    System.err.printf("\tgamma: %d\n", gamma);
    System.err.printf("\txi: %d\n", xi);
    System.err.printf("\tnThreshold: %e\n", nThreshold);
  }
  
  
  RVFDataset<String, String> getSamples(Random random) {
     return getSamples(random, new HashIndex<String>());
  }
  
  RVFDataset<String, String> getSamples(Random random, Index<String> featureIndex) {
    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestlists = MERT.nbest.nbestLists();
    Set<String> featureWhiteList = OptimizerUtils.featureWhiteList(MERT.nbest, minFeatureSegmentCount);
    int totalFeatureCount = OptimizerUtils.featureWhiteList(MERT.nbest, 0).size();
    System.err.printf("Min Feature Segment Count: %d Features Filterd to: %d from: %d\n", minFeatureSegmentCount, featureWhiteList.size(), totalFeatureCount);
    System.err.printf("White List Features:\n%s\n", featureWhiteList);
    Index<String> labelIndex = new HashIndex<String>();
    labelIndex.add("0");
    labelIndex.add("1");    
    RVFDataset<String,String> dataset = new RVFDataset<String, String>(xi*nbestlists.size(), featureIndex, labelIndex);
    
    for (int i = 0; i < nbestlists.size(); i++) {
      List<Pair<Double, Pair<Integer, Integer>>> v = new ArrayList<Pair<Double, Pair<Integer, Integer>>>();
      
      System.err.printf("Creating Eval Metric for list %d...\n", i);
      
      EvaluationMetric<IString, String> evalMetric = 
          EvaluationMetricFactory.newMetric(mert.evalMetric, mert.references.subList(i, i+1), true);
      
      System.err.printf("Sampling n-best list: %d\n", i);
      
      for (int g = 0; g < gamma; g++) {
          int jMax   = nbestlists.get(i).size();  
          int j      = random.nextInt(jMax); 
          int jPrime = random.nextInt(jMax);
          
          // sentence level evaluation metric
          double gJ = evalMetric.score(nbestlists.get(i).subList(j, j+1));
          double gJPrime = evalMetric.score(nbestlists.get(i).subList(jPrime, jPrime+1));
          double absDiff = Math.abs(gJ-gJPrime);
          if (absDiff >= nThreshold) {
            if (gJ > gJPrime) {
              v.add(new Pair<Double, Pair<Integer,Integer>>(absDiff, new Pair<Integer,Integer>(j, jPrime)));
            } else {
              v.add(new Pair<Double, Pair<Integer,Integer>>(absDiff, new Pair<Integer,Integer>(jPrime, j)));
            }
          }
      }
      Collections.sort(v);
      Collections.reverse(v);
      List<Pair<Double, Pair<Integer, Integer>>> selectedV = v.subList(0, Math.min(xi, v.size()));

      System.err.printf("\taccepted samples: %d\n", selectedV.size());
      
      for (Pair<Double, Pair<Integer, Integer>> selectedPair : selectedV) {
        Counter<String> plusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            nbestlists.get(i).get(selectedPair.second.first).features);
        Counter<String> minusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            nbestlists.get(i).get(selectedPair.second.second).features);
        Counter<String> gtVector = new ClassicCounter<String>(plusFeatures);
        Counters.subtractInPlace(gtVector, minusFeatures);
        
        Counters.retainKeys(gtVector, featureWhiteList);
        RVFDatum<String, String> datumGt = new RVFDatum<String, String>(gtVector, "1");
        
        Counter<String> ltVector = new ClassicCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);

        Counters.retainKeys(ltVector, featureWhiteList);
        RVFDatum<String, String> datumLt = new RVFDatum<String, String>(ltVector, "0");
        dataset.add(datumGt);
        dataset.add(datumLt);
      }
    }
    
    return dataset;
  }
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    Counters.normalize(wts);
    double seedSeed = Math.abs(Counters.max(wts));
    long seed = (long)Math.exp(Math.log(seedSeed) + Math.log(Long.MAX_VALUE));
    System.err.printf("PRO thread using random seed: %d\n", seed);
    RVFDataset<String, String> proSamples = getSamples(new Random(seed));
    LogPrior lprior = new LogPrior();
    lprior.setSigma(l2sigma);
    LogisticClassifierFactory<String,String> lcf = new LogisticClassifierFactory<String,String>();
    LogisticClassifier<String, String> lc = lcf.trainClassifier(proSamples, lprior, false);
    Counter<String> decoderWeights = new ClassicCounter<String>(); 
    Counter<String> lcWeights = lc.weightsAsCounter();
    for (String key : lcWeights.keySet()) {
      double mul;
      if (key.startsWith("1 / ")) {
        mul = 1.0;
      } else if (key.startsWith("0 / ")) {
        mul = -1.0;
      } else {
        throw new RuntimeException("Unparsable weight name produced by logistic classifier: "+key);
      }
      String decoderKey = key.replaceFirst("^[10] / ", "");
      decoderWeights.incrementCount(decoderKey, mul*lcWeights.getCount(key));
    }

    synchronized (MERT.bestWts) {
      if (!updatedBestOnce) {
        System.err.println("Force updating weights (once)");
        double metricEval = MERT.evalAtPoint(nbest, decoderWeights, emetric);
        MERT.updateBest(decoderWeights, metricEval, true);
        updatedBestOnce = true;
      }
    }
    return decoderWeights;
  }

}
