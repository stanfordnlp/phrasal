package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

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
import edu.stanford.nlp.util.Triple;

/**
 * Pairwise Ranking Optimization + SGD
 * 
 * @author Spence Green
 *
 */
public class PairwiseRankingOptimizerSGD implements OnlineOptimizer<IString,String> {

  private static final boolean DEBUG = true;

  // Batch defaults
  //  static public final int DEFAULT_GAMMA = 5000;
  //  static public final int DEFAULT_XI = 50;
  //  static public final double DEFAULT_N_THRESHOLD = 0.05;
  static public final int DEFAULT_GAMMA = 500;
  static public final int DEFAULT_XI = 15;
  static public final double DEFAULT_N_THRESHOLD = 5.0;
  static public final int DEFAULT_MIN_FEATURE_SEGMENT_COUNT = 3;

  // Logistic classifier labels
  private static final String POS_CLASS = "1";
  private static final String NEG_CLASS = "0";

  private final int gamma;
  private final int xi;
  private final double nThreshold;
  private final int minFeatureSegmentCount;
  private final int tuneSetSize;

  // TODO(spenceg): Make this configurable
  private final double learningRate = 0.1;
  private final double priorSigma = 0.1;
  
  private final LogPrior l2prior;
  private final LogPrior nullPrior;
  
  private final Logger logger;
  private final Random random;
  private final Index<String> featureIndex;
  private final Index<String> labelIndex;
  

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize) {
    this(featureIndex, tuneSetSize, DEFAULT_MIN_FEATURE_SEGMENT_COUNT, DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, String... args) {
    this(featureIndex, tuneSetSize, 
        args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
            args != null && args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_GAMMA,
                args != null && args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_XI,
                    args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_N_THRESHOLD);
  }

  public PairwiseRankingOptimizerSGD(Index<String> featureIndex, int tuneSetSize, int minFeatureSegmentCount, 
      int gamma, int xi, double nThreshold) {
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
    this.featureIndex = featureIndex;
    this.tuneSetSize = tuneSetSize;
    labelIndex = new HashIndex<String>();

    // Careful! Order is important here for LogisticObjectiveFunction.
    labelIndex.add(NEG_CLASS);
    labelIndex.add(POS_CLASS);
    labelIndex.lock();

    // Default: Gaussian prior
    this.l2prior = new LogPrior();
    this.l2prior.setSigma(priorSigma);
    this.nullPrior = new LogPrior(LogPriorType.NULL);

    random = new Random();

    // Setup the logger
    logger = Logger.getLogger(PairwiseRankingOptimizerSGD.class.getCanonicalName());
    OnlineTuner.attach(logger);
  }

  private RVFDataset<String, String> sampleNbestList(int sourceId, SentenceLevelMetric<IString, String> lossFunction, 
      List<RichTranslation<IString, String>> translations, List<Sequence<IString>> references) {
    assert sourceId >= 0;
    assert lossFunction != null;
    assert translations != null && translations.size() > 0;
    assert references.size() > 0;

    // TODO(spenceg): Filtering for sparse features. Don't need this for dense models.
    // Update when we switch to sparse models.
    //    Set<String> featureWhiteList = OptimizerUtils.featureWhiteList(MERT.nbest, minFeatureSegmentCount);
    //    int totalFeatureCount = OptimizerUtils.featureWhiteList(MERT.nbest, 0).size();
    //    System.err.printf("Min Feature Segment Count: %d Features Filterd to: %d from: %d\n", minFeatureSegmentCount, featureWhiteList.size(), totalFeatureCount);
    //    System.err.printf("White List Features:\n%s\n", featureWhiteList);

    RVFDataset<String,String> dataset = new RVFDataset<String, String>(xi, featureIndex, labelIndex);
    List<Triple<Double, Integer, Integer>> v = new ArrayList<Triple<Double, Integer, Integer>>();

    // Loss function is not threadsafe
    synchronized(lossFunction) {
      int jMax   = translations.size();  
      for (int g = 0; g < gamma; g++) {
        int j      = random.nextInt(jMax); 
        int jPrime = random.nextInt(jMax);

        // sentence level evaluation metric
        //        double gJ = evalMetric.score(nbestlists.get(i).subList(j, j+1));
        //        double gJPrime = evalMetric.score(nbestlists.get(i).subList(jPrime, jPrime+1));
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
      Collections.sort(v);
      Collections.reverse(v);
      List<Triple<Double, Integer, Integer>> selectedV = v.subList(0, Math.min(xi, v.size()));

      logger.info(String.format("Accepted samples: %d / %d", selectedV.size(), v.size()));
      if (DEBUG) {
        for (Triple<Double, Integer, Integer> sampledV : selectedV) {
          double margin = sampledV.first();
          int i = sampledV.second();
          int iPrime = sampledV.third();
          logger.info(String.format("%.02f %d %d || %s || %s", margin, i, iPrime, 
              translations.get(i).translation.toString(), translations.get(iPrime).translation.toString()));
        }
      }

      for (Triple<Double, Integer, Integer> selectedPair : selectedV) {
        Counter<String> plusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.second()).features);
        Counter<String> minusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.third()).features);
        Counter<String> gtVector = new ClassicCounter<String>(plusFeatures);
        Counters.subtractInPlace(gtVector, minusFeatures);
        // TODO(spenceg): Feature filtering
        //        Counters.retainKeys(gtVector, featureWhiteList);
        RVFDatum<String, String> datumGt = new RVFDatum<String, String>(gtVector, POS_CLASS);

        Counter<String> ltVector = new ClassicCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);
        // TODO(spenceg): Feature filtering
        //        Counters.retainKeys(ltVector, featureWhiteList);
        RVFDatum<String, String> datumLt = new RVFDatum<String, String>(ltVector, NEG_CLASS);
        dataset.add(datumGt);
        dataset.add(datumLt);
      }

      // Update the loss function with the 1-best translation
      lossFunction.update(sourceId, references, translations.get(0).translation);
    }

    return dataset;
  }


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

    RVFDataset<String, String> dataset = sampleNbestList(sourceId, lossFunction, translations, references);
    Counter<String> gradient = new ClassicCounter<String>();
    if (dataset.size() == 0) {
      logger.warning("Null gradient. No PRO samples for sourceId: " + sourceId);
    } else {
      LogisticObjectiveFunction lof = new LogisticObjectiveFunction(dataset.numFeatureTypes(), 
            dataset.getDataArray(), dataset.getValuesArray(), dataset.getLabelsArray(), nullPrior);
      double[] w = Counters.asArray(weights, featureIndex);
      double[] g = lof.derivativeAt(w);
      assert w.length == g.length;
      double dataFraction = dataset.size() / ((double) xi*tuneSetSize);
      l2prior.computeStochastic(w, g, dataFraction);
      gradient = Counters.toCounter(g, featureIndex);
      logger.info(String.format("Gradient (%d): %s", sourceId, gradient.toString()));
    }

    return gradient;
  }

  @Override
  public OnlineUpdateRule<String> newUpdater() {
    return new SGDUpdater(learningRate);
  }

  @Override
  public String toString() {
    return String.format("%s gamma: %d chi: %d thresh: %.2f", this.getClass().getSimpleName(), this.gamma, this.xi, this.nThreshold);
  }
}
