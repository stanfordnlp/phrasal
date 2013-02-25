package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import cern.colt.Arrays;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LogisticObjectiveFunction;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.classify.LogPrior.LogPriorType;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineTuner;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Triple;

/**
 * Pairwise Ranking Optimization + SGD
 * 
 * @author Spence Green
 *
 */
public class PairwiseRankingOptimizerSGD implements OnlineOptimizer<IString,String> {

  // Batch defaults
  //  static public final int DEFAULT_GAMMA = 5000;
  //  static public final int DEFAULT_XI = 50;
  //  static public final double DEFAULT_N_THRESHOLD = 0.05;
  public static final int DEFAULT_GAMMA = 500;
  public static final int DEFAULT_XI = 15;
  public static final double DEFAULT_N_THRESHOLD = 5.0;
  public static final int DEFAULT_MIN_FEATURE_SEGMENT_COUNT = 3;
  public static final double DEFAULT_SIGMA = 0.1;
  public static final double DEFAULT_RATE = 0.1;
  public static final String DEFAULT_UPDATER = "sgd";
  public static final double DEFAULT_L1 = 0;
  
  // Logistic classifier labels
  private static final String POS_CLASS = "POSITIVE";
  private static final String NEG_CLASS = "NEGATIVE";
  
  // PRO sampling and feature filtering
  private final int gamma;
  private final int xi;
  private final double nThreshold;
  private final int minFeatureSegmentCount;
  private final int tuneSetSize;

  private final double learningRate;
  private final String updaterType;

  // Regularization fields
  private final double L1lambda;
  private boolean l2Regularization;
  private final double sigmaSq;
  
  private final Logger logger;
  private final Random random;
  private final Index<String> featureIndex;
  private final Index<String> labelIndex;
  private final int expectedNumFeatures;

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, int expectedNumFeatures) {
    this(featureIndex, tuneSetSize, expectedNumFeatures, DEFAULT_MIN_FEATURE_SEGMENT_COUNT, 
        DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD, DEFAULT_SIGMA, DEFAULT_RATE, DEFAULT_UPDATER, DEFAULT_L1);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, int expectedNumFeatures, String... args) {
    this(featureIndex, tuneSetSize, expectedNumFeatures, 
        args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
            args != null && args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_GAMMA,
                args != null && args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_XI,
                    args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_N_THRESHOLD,
                        args != null && args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_SIGMA,
                            args != null && args.length > 5 ? Double.parseDouble(args[5]) : DEFAULT_RATE,
                            		args != null && args.length > 6 ? args[6] : DEFAULT_UPDATER,
                            				args != null && args.length > 7 ? Double.parseDouble(args[7]) : DEFAULT_L1);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, int expectedNumFeatures,
      int minFeatureSegmentCount, int gamma, int xi, double nThreshold, double sigma, double rate, String updaterType, double L1lambda) {
    if (minFeatureSegmentCount < 1) throw new RuntimeException("Feature segment count must be >= 1: " + minFeatureSegmentCount);
    if (gamma <= 0) throw new RuntimeException("Gamma must be > 0: " + gamma);
    if (xi <= 0) throw new RuntimeException("Xi must be > 0: " + xi);
    if (nThreshold < 0.0) throw new RuntimeException("Threshold must >= 0:" + nThreshold);
    
    this.expectedNumFeatures = expectedNumFeatures;
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
    this.featureIndex = featureIndex;
    this.tuneSetSize = tuneSetSize;
    this.learningRate = rate;
    this.updaterType = updaterType;
    random = new Random();

    // L1 regularization
    this.L1lambda = L1lambda;
    
    // L2 regularization
    this.l2Regularization = ! Double.isInfinite(sigma);
    this.sigmaSq = l2Regularization ? sigma*sigma : 0.0;
    
    // Careful! Order is important here for LogisticObjectiveFunction.
    labelIndex = new HashIndex<String>();
    labelIndex.add(NEG_CLASS);
    labelIndex.add(POS_CLASS);
    labelIndex.lock();

    // Setup the logger
    logger = Logger.getLogger(PairwiseRankingOptimizerSGD.class.getCanonicalName());
    OnlineTuner.attach(logger);
  }

  /**
   * Select PRO samples from a single instance.
   * 
   * @param sourceId
   * @param lossFunction
   * @param translations
   * @param references
   * @param featureWhitelist 
   * @return
   */
  private RVFDataset<String, String> sampleNbestList(int sourceId,
      SentenceLevelMetric<IString, String> lossFunction,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references) {
    int[] sourceIds = new int[1];
    sourceIds[0] = sourceId;
    List<List<RichTranslation<IString, String>>> translationList = new ArrayList<List<RichTranslation<IString, String>>>(1);
    translationList.add(translations);
    List<List<Sequence<IString>>> referenceList = new ArrayList<List<Sequence<IString>>>(1);
    referenceList.add(references);
    return sampleNbestLists(sourceIds, lossFunction, translationList, referenceList);
  }
  
  /**
   * Select PRO samples from a batch.
   * 
   * @param sourceIds
   * @param lossFunction
   * @param translationList
   * @param referenceList
   * @param featureWhitelist 
   * @return
   */
  private RVFDataset<String, String> sampleNbestLists(int[] sourceIds, SentenceLevelMetric<IString, String> lossFunction, 
      List<List<RichTranslation<IString, String>>> translationList, List<List<Sequence<IString>>> referenceList) {
    assert sourceIds != null;
    assert lossFunction != null;
    assert sourceIds.length == translationList.size();
    assert translationList.size() == referenceList.size();

    RVFDataset<String,String> dataset = new RVFDataset<String, String>(2*xi, featureIndex, labelIndex);

    for (int i = 0; i < sourceIds.length; ++i) {
      int sourceId = sourceIds[i];
      List<RichTranslation<IString, String>> translations = translationList.get(i);
      List<Sequence<IString>> references = referenceList.get(i);

      // Sample from this n-best list
      // Loss function is not threadsafe
      List<Triple<Double, Integer, Integer>> v;
      if (lossFunction.isThreadsafe()) {
        v = sample(translations, references, sourceId, lossFunction);
        lossFunction.update(sourceId, references, translations.get(0).translation);
       
      } else {
        synchronized(lossFunction) {
          v = sample(translations, references, sourceId, lossFunction);
          lossFunction.update(sourceId, references, translations.get(0).translation);
        }
      }

      // Get the max-margin pairs
      Collections.sort(v);
      Collections.reverse(v);
      List<Triple<Double, Integer, Integer>> selectedV = v.subList(0, Math.min(xi, v.size()));

      // Add selectedV to RVFDataset
      for (Triple<Double, Integer, Integer> selectedPair : selectedV) {
        Counter<String> plusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.second()).features);
        Counter<String> minusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.third()).features);
        Counter<String> gtVector = new OpenAddressCounter<String>(plusFeatures);
        Counters.subtractInPlace(gtVector, minusFeatures);
        
        RVFDatum<String, String> datumGt = new RVFDatum<String, String>(gtVector, POS_CLASS);
        dataset.add(datumGt);

        Counter<String> ltVector = new OpenAddressCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);
        
        RVFDatum<String, String> datumLt = new RVFDatum<String, String>(ltVector, NEG_CLASS);
        dataset.add(datumLt);

        // Debug info
        double margin = selectedPair.first();
        int j = selectedPair.second();
        int jPrime = selectedPair.third();
        logger.fine(String.format("%.02f %d %d %d || %s || %s", margin, i, j, jPrime,  
            translationList.get(i).get(j).translation.toString(), 
            translationList.get(i).get(jPrime).translation.toString()));
      }
    }
    return dataset;
  }

  /**
   * Sampling algorithm of Hopkins and May (2011).
   * 
   * @param translations
   * @param references
   * @param sourceId
   * @param lossFunction
   * @return
   */
  private List<Triple<Double, Integer, Integer>> sample(List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, int sourceId, SentenceLevelMetric<IString, String> lossFunction) {
    List<Triple<Double, Integer, Integer>> v = 
        new ArrayList<Triple<Double, Integer, Integer>>(gamma);
    int jMax   = translations.size();  
    for (int g = 0; g < gamma; g++) {
      int j      = random.nextInt(jMax); 
      int jPrime = random.nextInt(jMax);
      double gJ = lossFunction.score(sourceId, references, translations.get(j).translation);
      double gJPrime = lossFunction.score(sourceId,  references, translations.get(jPrime).translation);
      double absDiff = Math.abs(gJ-gJPrime);
      if (absDiff >= nThreshold) {
        if (gJ > gJPrime) {
          v.add(new Triple<Double, Integer,Integer>(absDiff, j, jPrime));
        } else {
          v.add(new Triple<Double, Integer,Integer>(absDiff, jPrime, j));
        }
      }
    }
    return v;
  }

  /**
   * True online learning, one example at a time.
   */
  @Override
  public Counter<String> getGradient(Counter<String> weights, Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations, List<Sequence<IString>> references, 
      SentenceLevelMetric<IString, String> lossFunction) {
    // TODO(spenceg): Sanity checking. For public methods, replace with exceptions.
    assert weights != null;
    assert sourceId >= 0;
    assert translations.size() > 0;
    assert references.size() > 0;
    assert lossFunction != null;

    // Sample from the n-best list
    RVFDataset<String, String> dataset = sampleNbestList(sourceId, lossFunction, translations, references);
    Counter<String> gradient = dataset.size() == 0 ? new OpenAddressCounter<String>() :
      computeGradient(dataset, weights, 1);
    if (dataset.size() == 0) {
      logger.warning("Null gradient for sourceId: " + sourceId);
    } else {
//      logger.fine("Gradient: " + gradient.toString());
    }
    return gradient;
  }

  /**
   * Mini-batch learning.
   */
  @Override
  public Counter<String> getBatchGradient(Counter<String> weights,
      List<Sequence<IString>> sources, int[] sourceIds,
      List<List<RichTranslation<IString, String>>> translations,
      List<List<Sequence<IString>>> references,
      SentenceLevelMetric<IString, String> lossFunction) {
    // TODO(spenceg): Sanity checking. For public methods, replace with exceptions.
    assert weights != null;
    assert sourceIds != null;
    assert translations.size() > 0;
    assert references.size() > 0;
    assert lossFunction != null;

    RVFDataset<String, String> dataset = sampleNbestLists(sourceIds, lossFunction, translations, references);
    Counter<String> gradient = dataset.size() == 0 ? new OpenAddressCounter<String>() :
      computeGradient(dataset, weights, sourceIds.length);
    if (dataset.size() == 0) {
      logger.warning("Null gradient for mini-batch: " + Arrays.toString(sourceIds));
    } else {
//      logger.fine("Gradient: " + gradient.toString());
    }
    return gradient;
  }

  /**
   * Compute the gradient for the specified set of PRO samples.
   * 
   * @param dataset
   * @param weights
   * @param batchSize
   * @return
   */
  private Counter<String> computeGradient(RVFDataset<String, String> dataset, Counter<String> weights, 
      int batchSize) {
    double dataFraction = dataset.size() / ((double) 2*xi*tuneSetSize);
//    LogPrior prior = new LogPrior(LogPriorType.QUADRATIC); // Gaussian prior
//    // Divide by the data fraction to get the same effect as scaling the regularization
//    // strength by the data fraction.
//    prior.setSigmaSquared(sigmaSq / dataFraction);
//    
    final int dimension = Math.max(weights.size(), dataset.numFeatureTypes());
    assert dimension <= featureIndex.size();
    
//    LogisticObjectiveFunction lof = new LogisticObjectiveFunction(dimension, 
//        dataset.getDataArray(), dataset.getValuesArray(), dataset.getLabelsArray(), prior);
//
//    double[] w = Counters.asArray(weights, featureIndex, dimension);
//    double[] g = lof.derivativeAt(w);
//    assert w.length == g.length;
//    Counter<String> gradient = toCounter(g, featureIndex);
    
    Counter<String> gradient = logisticDerivativeL2(weights, dataset.getDataArray(), dataset.getValuesArray(), 
        dataset.getLabelsArray(), sigmaSq / dataFraction, dimension);

    return gradient;
  }

  /**
   * Copied from LogisticObjectiveFunction.calculateRVF.
   * 
   * @param weights
   * @param dataArray
   * @param valuesArray
   * @param labels
   * @param scaledSigmaSquared
   * @return
   */
  protected Counter<String> logisticDerivativeL2(Counter<String> weights, int[][] dataArray, 
      double[][] valuesArray, int[] labels, double scaledSigmaSquared, int dimension) {

//    double value = 0.0;
      Counter<String> derivative = null;//new OpenAddressCounter<String>(dimension, 1.0f);

    for (int d = 0; d < dataArray.length; d++) {
      int[] features = dataArray[d];
      double[] values = valuesArray[d];
      double sum = 0;

      for (int f = 0; f < features.length; f++) {
        String key = featureIndex.get(features[f]);
        assert key != null;
        sum += weights.getCount(key)*values[f];
      }

      double expSum, derivativeIncrement;

      if (labels[d] == 0) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

//      if (dataweights == null) {
//        value += Math.log(1.0 + expSum);
//      } else {
//        value += Math.log(1.0 + expSum) * dataweights[d];
//        derivativeIncrement *= dataweights[d];
//      }

      for (int f = 0; f < features.length; f++) {
        String key = featureIndex.get(features[f]);
        derivative.incrementCount(key, values[f]*derivativeIncrement);
      }
    }
    
    // Add L2 regularization directly into the derivative
    if (this.l2Regularization) {
      for (int i = 0; i < dimension; ++i) {
        String key = featureIndex.get(i);
        if (key != null) {
          double x = weights.getCount(key);
          derivative.incrementCount(key, x / scaledSigmaSquared);
        }
      }
    }
    
    return derivative;
  }
  
  
  /**
   * Convert a double array to a Counter.
   * 
   * @param counts
   * @param index
   * @return
   */
  private Counter<String> toCounter(double[] counts, Index<String> index) {
    if (index.size() < counts.length)
      throw new IllegalArgumentException("Index not large enough to name all the array elements!");
    Counter<String> c = null;//new OpenAddressCounter<String>(counts.length, 1.0f);
    for (int i = 0; i < counts.length; i++) {
      if (counts[i] != 0.0) {
        c.setCount(index.get(i), counts[i]);
      }
    }
    return c;
  }
  
  @Override
  public OnlineUpdateRule<String> newUpdater() {
	if(this.updaterType.equals("adagrad"))
		return new AdaGradUpdater(learningRate, expectedNumFeatures);
	if(this.updaterType.equals("adagradl1"))
		return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures, L1lambda);
	return new SGDUpdater(learningRate);
  }

  @Override
  public String toString() {
    return String.format("%s gamma: %d xi: %d threshold: %.2f feature-filter: %d updater: %s", this.getClass().getSimpleName(), 
        this.gamma, this.xi, this.nThreshold, this.minFeatureSegmentCount, this.updaterType);
  }
}
