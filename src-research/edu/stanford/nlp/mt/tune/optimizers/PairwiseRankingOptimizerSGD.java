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
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

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
  static public final int DEFAULT_GAMMA = 50;
  static public final int DEFAULT_XI = 5;
  static public final double DEFAULT_N_THRESHOLD = 5.0;
  static public final int DEFAULT_MIN_FEATURE_SEGMENT_COUNT = 3;
 
  
  private final int gamma;
  private final int xi;
  private final double nThreshold;
  private final int minFeatureSegmentCount;

  private final LogPrior lprior;
  private final Logger logger;
  private final Random random;
  
  public PairwiseRankingOptimizerSGD() {
    this(DEFAULT_MIN_FEATURE_SEGMENT_COUNT, DEFAULT_GAMMA, DEFAULT_XI, DEFAULT_N_THRESHOLD);
  }
  
  public PairwiseRankingOptimizerSGD(String... args) {
    this(args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT,
        args != null && args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_GAMMA,
            args != null && args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_XI,
                args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_N_THRESHOLD);
  }
  
  public PairwiseRankingOptimizerSGD(int minFeatureSegmentCount, int gamma, int xi, double nThreshold) {
    this.gamma = gamma;
    this.xi = xi;
    this.nThreshold = nThreshold;
    this.minFeatureSegmentCount = minFeatureSegmentCount;
    
    // Uniform prior for stochastic optimization
    this.lprior = new LogPrior(LogPriorType.NULL);
    this.lprior.setSigma(0.0);
    
    random = new Random();
    
    // Setup the logger
    logger = Logger.getLogger(PairwiseRankingOptimizerSGD.class.getCanonicalName());
    OnlineTuner.attach(logger);
  }
  
  
  private RVFDataset<String, String> getSamples(int sourceId, SentenceLevelMetric<IString, String> lossFunction, 
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
    
    RVFDataset<String,String> dataset = new RVFDataset<String, String>(xi);
    List<Pair<Double, Pair<Integer, Integer>>> v = new ArrayList<Pair<Double, Pair<Integer, Integer>>>();

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
            v.add(new Pair<Double, Pair<Integer,Integer>>(absDiff, new Pair<Integer,Integer>(j, jPrime)));
          } else {
            v.add(new Pair<Double, Pair<Integer,Integer>>(absDiff, new Pair<Integer,Integer>(jPrime, j)));
          }
        }
      }
      Collections.sort(v);
      Collections.reverse(v);
      List<Pair<Double, Pair<Integer, Integer>>> selectedV = v.subList(0, Math.min(xi, v.size()));

      logger.info(String.format("Accepted samples: %d / %d", selectedV.size(), v.size()));
      if (DEBUG) {
        for (Pair<Double, Pair<Integer, Integer>> sampledV : selectedV) {
          double margin = sampledV.first();
          int i = sampledV.second().first();
          int iPrime = sampledV.second().second();
          logger.info(String.format("%.02f %d %d || %s || %s", margin, i, iPrime, 
              translations.get(i).translation.toString(), translations.get(iPrime).translation.toString()));
        }
      }

      for (Pair<Double, Pair<Integer, Integer>> selectedPair : selectedV) {
        Counter<String> plusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.second.first).features);
        Counter<String> minusFeatures = OptimizerUtils.featureValueCollectionToCounter(
            translations.get(selectedPair.second.second).features);
        Counter<String> gtVector = new ClassicCounter<String>(plusFeatures);
        Counters.subtractInPlace(gtVector, minusFeatures);
// TODO(spenceg): Feature filtering
//        Counters.retainKeys(gtVector, featureWhiteList);
        RVFDatum<String, String> datumGt = new RVFDatum<String, String>(gtVector, "1");

        Counter<String> ltVector = new ClassicCounter<String>(minusFeatures);
        Counters.subtractInPlace(ltVector, plusFeatures);
// TODO(spenceg): Feature filtering
//        Counters.retainKeys(ltVector, featureWhiteList);
        RVFDatum<String, String> datumLt = new RVFDatum<String, String>(ltVector, "0");
        dataset.add(datumGt);
        dataset.add(datumLt);
      }
      
      // Update the loss function with the 1-best translation
      lossFunction.update(sourceId, references, translations.get(0).translation);
    }
    
    return dataset;
  }
  

  /**
   * Evaluate the objective function.
   * 
   * TODO(spenceg): Inefficient. Pull out the objective function and evaluate directly
   * without converting to/from a counter.
   * 
   * @param weights
   * @param lof
   * @param index
   * @return
   */
  private Counter<String> getGradientFor(Counter<String> weights,
      LogisticObjectiveFunction lof, Index<String> index) {
    assert lof != null;
    assert index != null && index.size() > 0;
    
    double[] w = Counters.asArray(weights, index);
    assert w.length == weights.keySet().size();
    assert w.length == index.size();
    
    double[] gradient = lof.derivativeAt(w);
    assert gradient.length == w.length;
    
    return Counters.toCounter(gradient, index);
  }
  
  @Override
  public Counter<String> getGradient(Counter<String> weights, Sequence<IString> source,
      int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, SentenceLevelMetric<IString, String> lossFunction) {
    assert weights != null;
    assert sourceId >= 0;
    assert translations.size() > 0;
    assert references.size() > 0;
    assert lossFunction != null;
    
//    Counter<String> wts = new ClassicCounter<String>(initialWts);
//    Counters.normalize(wts);
//    double seedSeed = Math.abs(Counters.max(wts));
//    long seed = (long)Math.exp(Math.log(seedSeed) + Math.log(Long.MAX_VALUE));
//    System.err.printf("PRO thread using random seed: %d\n", seed);
    RVFDataset<String, String> data = getSamples(sourceId, lossFunction, translations, references);
//    LogisticClassifierFactory<String,String> lcf = new LogisticClassifierFactory<String,String>();
//    LogisticClassifier<String, String> lc = lcf.trainClassifier(data, lprior, false);
//    
    Counter<String> gradient = new ClassicCounter<String>();
    if (data.size() == 0) {
      logger.warning("Null gradient. No PRO samples for sourceId: " + sourceId);
    } else {
      // This is a batch gradient function
      LogisticObjectiveFunction lof = new LogisticObjectiveFunction(data.numFeatureTypes(), 
          data.getDataArray(), data.getValuesArray(), data.getLabelsArray(), lprior);

      gradient = getGradientFor(weights, lof, data.featureIndex());
      assert gradient.keySet().size() == weights.keySet().size();
    }
    
    return gradient;
    
    
    // Extracting final weights
    // TODO(spenceg): Move this to the Updater. This needs to be performed for every gradient
    // update, I think.
//    Counter<String> decoderWeights = new ClassicCounter<String>(); 
//    Counter<String> lcWeights = null;// = lc.weightsAsCounter();
//    for (String key : lcWeights.keySet()) {
//      double mul;
//      if (key.startsWith("1 / ")) {
//        mul = 1.0;
//      } else if (key.startsWith("0 / ")) {
//        mul = -1.0;
//      } else {
//        throw new RuntimeException("Unparsable weight name produced by logistic classifier: "+key);
//      }
//      String decoderKey = key.replaceFirst("^[10] / ", "");
//      decoderWeights.incrementCount(decoderKey, mul*lcWeights.getCount(key));
//    }

//    return decoderWeights;
  }


  @Override
  public OnlineUpdateRule<String> newUpdater() {
    return new SGDUpdater(0.1);
  }

}
