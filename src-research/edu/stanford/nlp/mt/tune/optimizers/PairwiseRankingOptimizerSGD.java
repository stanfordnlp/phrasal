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
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Quadruple;

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

  // Logistic classifier labels
  private static final String POS_CLASS = "POSITIVE";
  private static final String NEG_CLASS = "NEGATIVE";

  private final int gamma;
  private final int xi;
  private final double nThreshold;
  private final int minFeatureSegmentCount;
  private final int tuneSetSize;

  private final double learningRate;
  private final double sigmaSq;

  private final Logger logger;
  private final Random random;
  private final Index<String> featureIndex;
  private final Index<String> labelIndex;

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize) {
    this(featureIndex, tuneSetSize, DEFAULT_MIN_FEATURE_SEGMENT_COUNT, 
        DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD, DEFAULT_SIGMA, DEFAULT_RATE);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, String... args) {
    this(featureIndex, tuneSetSize, 
        args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
            args != null && args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_GAMMA,
                args != null && args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_XI,
                    args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_N_THRESHOLD,
                        args != null && args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_SIGMA,
                            args != null && args.length > 5 ? Double.parseDouble(args[5]) : DEFAULT_RATE);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, int minFeatureSegmentCount, 
      int gamma, int xi, double nThreshold, double sigma, double rate) {
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
    this.featureIndex = featureIndex;
    this.tuneSetSize = tuneSetSize;
    this.sigmaSq = sigma*sigma;
    this.learningRate = rate;
    random = new Random();

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
   * @return
   */
  private RVFDataset<String, String> sampleNbestLists(int[] sourceIds, SentenceLevelMetric<IString, String> lossFunction, 
      List<List<RichTranslation<IString, String>>> translationList, List<List<Sequence<IString>>> referenceList) {
    assert sourceIds != null;
    assert lossFunction != null;
    assert sourceIds.length == translationList.size();
    assert translationList.size() == referenceList.size();

    // TODO(spenceg): Filtering for sparse features. Don't need this for dense models.
    // Update when we switch to sparse models.
    //    Set<String> featureWhiteList = OptimizerUtils.featureWhiteList(MERT.nbest, minFeatureSegmentCount);
    //    int totalFeatureCount = OptimizerUtils.featureWhiteList(MERT.nbest, 0).size();
    //    System.err.printf("Min Feature Segment Count: %d Features Filterd to: %d from: %d\n", minFeatureSegmentCount, featureWhiteList.size(), totalFeatureCount);
    //    System.err.printf("White List Features:\n%s\n", featureWhiteList);

    RVFDataset<String,String> dataset = new RVFDataset<String, String>(2*xi, featureIndex, labelIndex);

    for (int i = 0; i < sourceIds.length; ++i) {
      int sourceId = sourceIds[i];
      List<RichTranslation<IString, String>> translations = translationList.get(i);
      List<Sequence<IString>> references = referenceList.get(i);

      // Sample from this n-best list
      // Loss function is not threadsafe
      List<Quadruple<Double, Integer, Integer,Integer>> v = 
          new ArrayList<Quadruple<Double, Integer, Integer, Integer>>(gamma);
      synchronized(lossFunction) {
        int jMax   = translations.size();  
        for (int g = 0; g < gamma; g++) {
          int j      = random.nextInt(jMax); 
          int jPrime = random.nextInt(jMax);
          double gJ = lossFunction.score(sourceId, references, translations.get(j).translation);
          double gJPrime = lossFunction.score(sourceId,  references, translations.get(jPrime).translation);
          double absDiff = Math.abs(gJ-gJPrime);
          if (absDiff >= nThreshold) {
            if (gJ > gJPrime) {
              v.add(new Quadruple<Double, Integer,Integer,Integer>(absDiff, j, jPrime, i));
            } else {
              v.add(new Quadruple<Double, Integer,Integer,Integer>(absDiff, jPrime, j, i));
            }
          }
        }
        // Update the loss function with the 1-best translation
        lossFunction.update(sourceId, references, translations.get(0).translation);
      }

      // Get the max-margin pairs
      Collections.sort(v);
      Collections.reverse(v);
      List<Quadruple<Double, Integer, Integer, Integer>> selectedV = v.subList(0, Math.min(xi, v.size()));

      // Add selectedV to RVFDataset
      for (Quadruple<Double, Integer, Integer, Integer> selectedPair : selectedV) {
        Counter<String> plusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.second()).features);
        Counter<String> minusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.third()).features);
        Counter<String> gtVector = new ClassicCounter<String>(plusFeatures);
        Counters.subtractInPlace(gtVector, minusFeatures);
        // TODO(spenceg): Feature filtering
        //        Counters.retainKeys(gtVector, featureWhiteList);
        RVFDatum<String, String> datumGt = new RVFDatum<String, String>(gtVector, POS_CLASS);
        dataset.add(datumGt);

        Counter<String> ltVector = new ClassicCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);
        // TODO(spenceg): Feature filtering
        //        Counters.retainKeys(ltVector, featureWhiteList);
        RVFDatum<String, String> datumLt = new RVFDatum<String, String>(ltVector, NEG_CLASS);
        dataset.add(datumLt);

        // WSGDEBUG Debug info
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
    Counter<String> gradient = dataset.size() == 0 ? new ClassicCounter<String>() :
      computeGradient(dataset, weights, 1);
    if (dataset.size() == 0) {
      logger.warning("Null gradient for sourceId: " + sourceId);
    } else {
      logger.info("Gradient: " + gradient.toString());
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

    RVFDataset<String, String> dataset = sampleNbestLists(sourceIds, lossFunction, translations, references);
    Counter<String> gradient = dataset.size() == 0 ? new ClassicCounter<String>() :
      computeGradient(dataset, weights, sourceIds.length);
    if (dataset.size() == 0) {
      logger.warning("Null gradient for mini-batch: " + Arrays.toString(sourceIds));
    } else {
      logger.info("Gradient: " + gradient.toString());
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
    LogPrior prior = new LogPrior(LogPriorType.QUADRATIC); // Gaussian prior
    // Divide by the data fraction to get the same effect as scaling the regularization
    // strength by the data fraction.
    prior.setSigmaSquared(sigmaSq / dataFraction);
    LogisticObjectiveFunction lof = new LogisticObjectiveFunction(dataset.numFeatureTypes(), 
        dataset.getDataArray(), dataset.getValuesArray(), dataset.getLabelsArray(), prior);

    double[] w = Counters.asArray(weights, featureIndex);
    double[] g = lof.derivativeAt(w);
    assert w.length == g.length;
    Counter<String> gradient = Counters.toCounter(g, featureIndex);
    return gradient;
  }

  @Override
  public OnlineUpdateRule<String> newUpdater() {
    return new SGDUpdater(learningRate);
  }

  @Override
  public String toString() {
    return String.format("%s gamma: %d chi: %d thresh: %.2f", this.getClass().getSimpleName(), this.gamma, 
        this.xi, this.nThreshold);
  }
}
