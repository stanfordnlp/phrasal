package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineOptimizer;
import edu.stanford.nlp.mt.tune.OnlineUpdateRule;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SystemLogger;
import edu.stanford.nlp.mt.util.SystemLogger.LogName;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.OpenAddressCounter;

/**
 * 1-best MIRA training with hope/fear translations. See Crammer et al. (2006),
 * Watanabe et al. (2007), Chiang (2012), and Eidelman (2012).
 * 
 * 
 * @author Spence Green
 */
public class MIRA1BestHopeFearOptimizer implements OnlineOptimizer<IString,String> {

  /**
   * Default aggressiveness parameter.
   */
  public static final double DEFAULT_C = 0.01;

  private final double C;
  
  private final Logger logger;

  public MIRA1BestHopeFearOptimizer(double C) {
    this.C = C;
    logger = Logger.getLogger(MIRA1BestHopeFearOptimizer.class.getCanonicalName());
    SystemLogger.attach(logger, LogName.ONLINE);
  }

  public MIRA1BestHopeFearOptimizer(String... args) {
    C = (args == null || args.length != 1) ? DEFAULT_C : Double.parseDouble(args[0]);
    logger = Logger.getLogger(MIRA1BestHopeFearOptimizer.class.getCanonicalName());
    SystemLogger.attach(logger, LogName.ONLINE);
  }
  
  @Override
  public OnlineUpdateRule<String> newUpdater() {
    return new MiraUpdater();
  }

  /**
   * This is an implementation of Fig.2 from Crammer et al. (2006).
   */
  @Override
  public Counter<String> getGradient(Counter<String> weights, 
      Sequence<IString> source,
      int sourceId,
      List<RichTranslation<IString, String>> translations, List<Sequence<IString>> references, 
      double[] referenceWeights, SentenceLevelMetric<IString, String> scoreMetric) {
    
    // Lock the loss function since we don't want updates to its statistics while we are searching
    // for the hope and fear derivations.
    Derivation dHope, dFear;
    synchronized(scoreMetric) {
      // The "correct" derivation (Crammer et al. (2006) fig.2)
      dHope = getBestHopeDerivation(scoreMetric, translations, references, referenceWeights, sourceId, source);
      logger.fine("Hope derivation: " + dHope.toString());

      // The "max-loss" derivation (Crammer et al. (2006) fig.2)
      dFear = getBestFearDerivation(scoreMetric, translations, references, referenceWeights, dHope, sourceId, source);
      logger.fine("Fear derivation: " + dFear.toString());

      // Update the loss function with the hope derivation a la
      // Cherry and Foster (2012) (Chiang (2012) uses the 1-best translation).
      // This follows the Moses implementation in mert/kbmira.cpp
      if (dHope.nbestId != dFear.nbestId) {
        scoreMetric.update(sourceId, references, dHope.hypothesis.translation);
      }
    }
    
    final double margin = dFear.modelScore - dHope.modelScore;
    // TODO(spenceg): Crammer takes the square root of the cost. We should try that.
    final double deltaCost = dHope.gain - dFear.gain;
    final double loss = margin + deltaCost;
    logger.info(String.format("Margin: %.5f dCost: %.5f Loss: %.5f", margin, deltaCost, loss));

    Counter<String> gradient = new OpenAddressCounter<String>();

    // Hinge loss.
    if (loss > 0.0) {
      // Only do an update in this case.
      // Compute the PA-II update, which is the loss divided by the 
      // squared norm of the differences between the feature vectors
      Counter<String> hopeFeatures = OptimizerUtils.featureValueCollectionToCounter(dHope.hypothesis.features);
      Counter<String> fearFeatures = OptimizerUtils.featureValueCollectionToCounter(dFear.hypothesis.features);
      gradient = Counters.diff(hopeFeatures, fearFeatures);
      logger.fine("Feature difference: " + gradient.toString());
      
      // Compute the update
      double sumSquaredFeatureDiff = Counters.sumSquares(gradient);
      double tau = Math.min(C, loss / sumSquaredFeatureDiff);
      logger.fine(String.format("tau: %e", tau));
      
      // Update the weights
      Counters.multiplyInPlace(gradient, tau);
    
    } else {
      logger.info(String.format("NO UPDATE (loss: %e)", loss));
    }
    
    return gradient;
  }

  /**
   * Max model score - cost
   * @param scoreMetric 
   * 
   * @param translations
   * @param references 
   * @param source 
   * @return
   */
  private static Derivation getBestHopeDerivation(SentenceLevelMetric<IString, String> scoreMetric, 
      List<RichTranslation<IString,String>> translations,
      List<Sequence<IString>> references, 
      double[] referenceWeights,
      int translationId, Sequence<IString> source) {

    RichTranslation<IString,String> d = null;
    double dCost = 0.0;
    int dId = 0;
    double maxScore = Double.NEGATIVE_INFINITY;
    int nbestId = 0;
    for (RichTranslation<IString,String> hypothesis : translations) {
      double gain = scoreMetric.score(translationId, source, references, hypothesis.translation);
      double modelScore = hypothesis.score;
      double score = modelScore + gain;
      
      // argmax
      if (score > maxScore) {
        d = hypothesis;
        dCost = gain;
        dId = nbestId;
        maxScore = score;
      }
      ++nbestId;
    }

    assert d != null;
    return new Derivation(d, dCost, dId);
  }

  /**
   * Max model score + cost
   * @param scoreMetric 
   * 
   * @param translations
   * @param references 
   * @param source 
   * @return
   */
  private Derivation getBestFearDerivation(SentenceLevelMetric<IString, String> scoreMetric, 
      List<RichTranslation<IString,String>> translations, 
      List<Sequence<IString>> references, 
      double[] referenceWeights,
      Derivation dHope,
      int translationId, Sequence<IString> source) {
    RichTranslation<IString,String> d = null;
//    final double hopeCost = lossFunction.score(translationId, references, dHope.hypothesis.translation);
    double dScore = 0.0;
    double dCost = 0.0;
    int dId = -1;
    double maxScore = Double.NEGATIVE_INFINITY;
    int nbestId = 0;
    for (RichTranslation<IString,String> hypothesis : translations) {
      double gain = scoreMetric.score(translationId, source, references, hypothesis.translation);
      double modelScore = hypothesis.score;
      double score = modelScore - gain;
      
      // Chiang (2012) calculation
//      double loss = hopeCost - cost;
//      double scoreDiff = dHope.hypothesis.score - hypothesis.score;
//      double score = loss - scoreDiff;
      // argmax
      if (score > maxScore && nbestId != dHope.nbestId) {
        d = hypothesis;
        dCost = gain;
        maxScore = score;
        dId = nbestId;
      }
      ++nbestId;
    }

    if (d == null) {
      // Logger is threadsafe. No worries.
      logger.warning("No fear derivation for: " + translationId);
    }
    
    return d == null ? dHope : new Derivation(d, dCost, dId);
  }
  
  @Override
  public String toString() {
    return String.format("%s with C: %.4f", this.getClass().getSimpleName(), C);    
  }

  /**
   * Convenience class for storing a hypothesis and relevant quantities.
   * 
   * @author Spence Green
   *
   */
  private static class Derivation {
    public final RichTranslation<IString,String> hypothesis;
    public final double modelScore;
    public final double gain;
    public final int nbestId;

    public Derivation(RichTranslation<IString,String> hypothesis, 
        double gain, int nbestId) {
      this.hypothesis = hypothesis;
      this.modelScore = hypothesis.score;
      this.gain = gain;
      this.nbestId = nbestId;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      hypothesis.nbestToMosesStringBuilder(nbestId, sb, null, false, false);
      return String.format("Score: %.4f Model: %.4f id: %s", 
          gain, modelScore, sb.toString());
    }
  }

  @Override
  public Counter<String> getBatchGradient(Counter<String> weights,
      List<Sequence<IString>> sources, int[] sourceIds,
      List<List<RichTranslation<IString, String>>> translations,
      List<List<Sequence<IString>>> references,
      double[] referenceWeights, SentenceLevelMetric<IString, String> scoreMetric) {
    throw new UnsupportedOperationException("1-best MIRA does not support mini-batch learning");
  }
}
