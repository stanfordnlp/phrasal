package edu.stanford.nlp.mt.tune.optimizers;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineTuner;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.Generics;
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
  public static final String DEFAULT_REGCONFIG="";
  public static final boolean VERBOSE = true;
  
  // Logistic classifier labels
  private static enum Label {POSITIVE, NEGATIVE}

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
  private final String regconfig;

  private final Logger logger;
  private final Random random;
  private final int expectedNumFeatures;


  public PairwiseRankingOptimizerSGD(int tuneSetSize, int expectedNumFeatures) {
    this(tuneSetSize, expectedNumFeatures, DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
        DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD, DEFAULT_SIGMA, DEFAULT_RATE, DEFAULT_UPDATER, DEFAULT_L1, DEFAULT_REGCONFIG);
  }

  public PairwiseRankingOptimizerSGD(int tuneSetSize, int expectedNumFeatures, String... args) {
    this(tuneSetSize, expectedNumFeatures,
        args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
        args != null && args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_GAMMA,
        args != null && args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_XI,
        args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_N_THRESHOLD,
        args != null && args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_SIGMA,
        args != null && args.length > 5 ? Double.parseDouble(args[5]) : DEFAULT_RATE,
        args != null && args.length > 6 ? args[6] : DEFAULT_UPDATER,
        args != null && args.length > 7 ? Double.parseDouble(args[7]) : DEFAULT_L1,
        args != null && args.length > 8 ? args[8] : DEFAULT_REGCONFIG);
  }

  public PairwiseRankingOptimizerSGD(int tuneSetSize, int expectedNumFeatures,
      int minFeatureSegmentCount, int gamma, int xi, double nThreshold, double sigma, double rate, String updaterType, double L1lambda, String regconfig) {
    if (minFeatureSegmentCount < 1) throw new RuntimeException("Feature segment count must be >= 1: " + minFeatureSegmentCount);
    if (gamma <= 0) throw new RuntimeException("Gamma must be > 0: " + gamma);
    if (xi <= 0) throw new RuntimeException("Xi must be > 0: " + xi);
    if (nThreshold < 0.0) throw new RuntimeException("Threshold must >= 0:" + nThreshold);

    this.expectedNumFeatures = expectedNumFeatures;
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
    this.tuneSetSize = tuneSetSize;
    this.learningRate = rate;
    this.updaterType = updaterType;
    random = new Random();

    // L1 regularization
    this.L1lambda = L1lambda;
    this.regconfig = regconfig;

    // L2 regularization
    this.l2Regularization = ! Double.isInfinite(sigma);
    this.sigmaSq = l2Regularization ? sigma*sigma : 0.0;

    // Setup the logger
    logger = Logger.getLogger(PairwiseRankingOptimizerSGD.class.getCanonicalName());
    OnlineTuner.attach(logger);
  }

  /**
   * Select PRO samples from a single instance.
   *
   * @param sourceId
   * @param source 
   * @param scoreMetric
   * @param translations
   * @param references
   * @return
   */
  private List<Datum> sampleNbestList(int sourceId,
      Sequence<IString> source, SentenceLevelMetric<IString, String> scoreMetric,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references) {
    int[] sourceIds = new int[1];
    sourceIds[0] = sourceId;
    List<Sequence<IString>> sources = Generics.newArrayList(1);
    sources.add(source);
    List<List<RichTranslation<IString, String>>> translationList = new ArrayList<List<RichTranslation<IString, String>>>(1);
    translationList.add(translations);
    List<List<Sequence<IString>>> referenceList = new ArrayList<List<Sequence<IString>>>(1);
    referenceList.add(references);
    return sampleNbestLists(sourceIds, sources, scoreMetric, translationList, referenceList);
  }

  /**
   * Select PRO samples from a batch.
   *
   * @param sourceIds
   * @param sources
   * @param scoreMetric
   * @param translationList
   * @param referenceList
   * @return
   */
  private List<Datum> sampleNbestLists(int[] sourceIds, List<Sequence<IString>> sources,
      SentenceLevelMetric<IString, String> scoreMetric, List<List<RichTranslation<IString, String>>> translationList, List<List<Sequence<IString>>> referenceList) {
    assert sourceIds != null;
    assert scoreMetric != null;
    assert sourceIds.length == translationList.size();
    assert translationList.size() == referenceList.size();

    List<Datum> dataset = new ArrayList<Datum>(2*xi);

    for (int i = 0; i < sourceIds.length; ++i) {
      int sourceId = sourceIds[i];
      List<RichTranslation<IString, String>> translations = translationList.get(i);
      List<Sequence<IString>> references = referenceList.get(i);
      Sequence<IString> source = sources.get(i);
      
      // Sample from this n-best list
      // Loss function is not threadsafe
      List<Triple<Double, Integer, Integer>> v;
      if (scoreMetric.isThreadsafe()) {
        v = sample(translations, references, sourceId, source, scoreMetric);
        scoreMetric.update(sourceId, references, translations.get(0).translation);

      } else {
        synchronized(scoreMetric) {
          v = sample(translations, references, sourceId, source, scoreMetric);
          scoreMetric.update(sourceId, references, translations.get(0).translation);
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

        dataset.add(new Datum(Label.POSITIVE, gtVector));

        Counter<String> ltVector = new OpenAddressCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);

        dataset.add(new Datum(Label.NEGATIVE, ltVector));

        // Debug info
//        double margin = selectedPair.first();
//        int j = selectedPair.second();
//        int jPrime = selectedPair.third();
//        logger.fine(String.format("%.02f %d %d %d || %s || %s", margin, i, j, jPrime,
//            translationList.get(i).get(j).translation.toString(),
//            translationList.get(i).get(jPrime).translation.toString()));
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
   * @param source 
   * @param scoreMetric
   * @return
   */
  private List<Triple<Double, Integer, Integer>> sample(List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, int sourceId, Sequence<IString> source, SentenceLevelMetric<IString, String> scoreMetric) {
    List<Triple<Double, Integer, Integer>> v =
        new ArrayList<Triple<Double, Integer, Integer>>(gamma);
    int jMax   = translations.size();
    for (int g = 0; g < gamma; g++) {
      int j      = random.nextInt(jMax);
      int jPrime = random.nextInt(jMax);
      double gJ = scoreMetric.score(sourceId, source, references, translations.get(j).translation);
      double gJPrime = scoreMetric.score(sourceId, source, references, translations.get(jPrime).translation);
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
      double[] referenceWeights, SentenceLevelMetric<IString, String> scoreMetric) {
    // TODO(spenceg): Sanity checking. For public methods, replace with exceptions.
    assert weights != null;
    assert sourceId >= 0;
    assert translations.size() > 0;
    assert references.size() > 0;
    assert scoreMetric != null;

    // Sample from the n-best list
    List<Datum> dataset = sampleNbestList(sourceId, source, scoreMetric, translations, references);
    Counter<String> gradient = computeGradient(dataset, weights, 1);
    if (dataset.size() == 0) {
      logger.warning("Null gradient for sourceId: " + sourceId);
    }
    
    if (VERBOSE) {
       System.err.printf("True online gradient");
       displayGradient(gradient);
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
      double[] referenceWeights, SentenceLevelMetric<IString, String> scoreMetric) {
    // TODO(spenceg): Sanity checking. For public methods, replace with exceptions.
    assert weights != null;
    assert sourceIds != null;
    assert translations.size() > 0;
    assert references.size() > 0;
    assert scoreMetric != null;

    List<Datum> dataset = sampleNbestLists(sourceIds, sources, scoreMetric, translations, references);
    Counter<String> gradient = computeGradient(dataset, weights, sourceIds.length);
    if (dataset.isEmpty()) {
      logger.warning("Null gradient for mini-batch: " + Arrays.toString(sourceIds));
    }
    
    if (VERBOSE) {
       System.err.printf("N-best list sizes:\n");
       for (int i = 0; i < translations.size(); i++) {
          System.err.printf(" %d: %d\n", i, translations.get(i).size());
       }
       System.err.printf("Data set size: %d\n", dataset.size());
       System.err.println("Batch gradient");
       displayGradient(gradient);
    }
    
    return gradient;
  }

  private void displayGradient(Counter<String> gradient) {
     System.err.printf("Gradient: ");
     System.err.println(gradient);
  }
  
  /**
   * Compute the gradient for the specified set of PRO samples.
   *
   * @param dataset
   * @param weights
   * @param batchSize
   * @return
   */
  private Counter<String> computeGradient(List<Datum> dataset, Counter<String> weights,
      int batchSize) {

    Counter<String> gradient = new OpenAddressCounter<String>(weights.keySet().size(), 1.0f);

    for (Datum datum : dataset) {
      double sum = 0;
      for (String feature : datum.vX.keySet()) {
        sum += weights.getCount(feature)*datum.vX.getCount(feature);
      }

      double expSum, derivativeIncrement;

      if (datum.label == Label.NEGATIVE) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

      for (String feature : datum.vX.keySet()) {
        double g = datum.vX.getCount(feature)*derivativeIncrement;
        gradient.incrementCount(feature, g);
      }
    }

    // Add L2 regularization directly into the derivative
    if (this.l2Regularization && dataset.size() > 0) {
      final Set<String> features = new HashSet<String>(weights.keySet());
      features.addAll(gradient.keySet());
      final double dataFraction = dataset.size() / ((double) 2*xi*tuneSetSize);
      final double scaledSigmaSquared = sigmaSq / dataFraction;
      for (String key : features) {
        double x = weights.getCount(key);
        gradient.incrementCount(key, x / scaledSigmaSquared);
      }
    }

    return gradient;
  }


  private static class Datum {
    public Label label;
    public Counter<String> vX;
    public Datum(Label label, Counter<String> vX) {
      this.label = label;
      this.vX = vX;
    }
  }

  @Override
  public OnlineUpdateRule<String> newUpdater() {
	if(this.updaterType.equalsIgnoreCase("adagrad"))
	  return new AdaGradUpdater(learningRate, expectedNumFeatures);
	Counter<String> customl1 = new ClassicCounter<String>();
	try{
	  Scanner scanner = new Scanner(new FileReader(regconfig));
	  while (scanner.hasNextLine()) {
	    String[] columns = scanner.nextLine().split(" ");
	    customl1.incrementCount(columns[0], Double.parseDouble(columns[1]));
	  }
	  System.out.println("Using custom L1: "+customl1);
	}
	catch(FileNotFoundException ex)
	{
          System.out.println("Not using custom L1");
	}
	if(this.updaterType.equalsIgnoreCase("adagradl1"))
	    return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures, L1lambda, AdaGradFOBOSUpdater.Norm.LASSO, customl1);
        if(this.updaterType.equalsIgnoreCase("adagradElitistLasso"))
	  return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures, L1lambda, AdaGradFOBOSUpdater.Norm.aeLASSO, customl1);
	if(this.updaterType.equalsIgnoreCase("adagradl1f"))
        {
	  return new AdaGradFastFOBOSUpdater(learningRate, expectedNumFeatures, L1lambda, customl1);
	}
	return new SGDUpdater(learningRate);
  }

  @Override
  public String toString() {
    return String.format("%s gamma: %d xi: %d threshold: %.2f feature-filter: %d updater: %s", this.getClass().getSimpleName(),
        this.gamma, this.xi, this.nThreshold, this.minFeatureSegmentCount, this.updaterType);
  }
}
