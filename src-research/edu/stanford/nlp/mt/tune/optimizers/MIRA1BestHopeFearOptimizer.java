package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineTuner;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

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
    OnlineTuner.attach(logger);
    logger.info(String.format("1-best MIRA optimization with C: %e", C));
  }

  public MIRA1BestHopeFearOptimizer(String... args) {
    C = (args == null || args.length != 1) ? DEFAULT_C : Double.parseDouble(args[0]);
    logger = Logger.getLogger(MIRA1BestHopeFearOptimizer.class.getCanonicalName());
    OnlineTuner.attach(logger);
    logger.info(String.format("1-best MIRA optimization with C: %e", C));    
  }

  /**
   * This is an implementation of Fig.2 from Crammer et al. (2006).
   */
  @Override
  public Counter<String> update(Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references,
      SentenceLevelMetric<IString, String> lossFunction, Counter<String> weights) {
    
    // Weight vector that we will return
    final Counter<String> wts = new ClassicCounter<String>(weights);
    
    // The "correct" derivation (Crammer et al. (2006) fig.2)
    Derivation dHope = getBestHopeDerivation(lossFunction, translations, references, sourceId);
    logger.info("Hope derivation: " + dHope.toString());
    
    // The "max-loss" derivation (Crammer et al. (2006) fig.2)
    Derivation dFear = getBestFearDerivation(lossFunction, translations, references, dHope, sourceId);
    logger.info("Fear derivation: " + dFear.toString());
    
    double margin = dFear.score - dHope.score;
    // TODO(spenceg): Crammer takes the square root of the cost. We should try that.
    double cost = dHope.cost - dFear.cost;
    final double loss = margin + cost;
    logger.info(String.format("Loss: %e", loss));
    if (loss > 0.0) {
      // Only do an update in this case.
      // Compute the PA-II update, which is the loss divided by the 
      // squared norm of the differences between the feature vectors
      Counter<String> featureDiff = getFeatureDiff(dHope, dFear);
      logger.info("Feature difference: " + featureDiff.toString());
      double normSq = Counters.sumSquares(featureDiff);
      double tau = Math.min(C, loss / normSq);
      logger.info(String.format("tau: %e", tau));
      Counters.multiplyInPlace(featureDiff, tau);
      Counters.addInPlace(wts, featureDiff);
    
    } else {
      logger.info(String.format("No update (loss: %e)", loss));
    }
    
    // Update the loss function
    lossFunction.update(sourceId, references, translations.get(0).translation);
    
    return wts;
  }

  /**
   * Compute a vector containing the differences in feature values of the hope and fear derivations.
   * 
   * Note: these features are in log space?
   * 
   * @param d1
   * @param d2
   * @return
   */
  private static Counter<String> getFeatureDiff(Derivation d1, Derivation d2) {
    Counter<String> d1Feats = OptimizerUtils.featureValueCollectionToCounter(d1.hypothesis.features);
    Counter<String> d2Feats = OptimizerUtils.featureValueCollectionToCounter(d2.hypothesis.features);
    // TODO(spenceg): This assertion fails. Check it.
//    assert d1Feats.keySet().size() == d2Feats.keySet().size();
    for (String key : d1Feats.keySet()) {
//      assert d2Feats.containsKey(key);
      double value = d1Feats.getCount(key) - d2Feats.getCount(key);
      d1Feats.setCount(key, value);
    }
    return d1Feats;
  }

  /**
   * Max model score - cost
   * @param objective 
   * 
   * @param translations
   * @param references 
   * @return
   */
  private static Derivation getBestHopeDerivation(SentenceLevelMetric<IString, String> objective, List<RichTranslation<IString,String>> translations,
      List<Sequence<IString>> references, int translationId) {

    RichTranslation<IString,String> d = null;
    double dScore = 0.0;
    double dCost = 0.0;
    int dId = 0;
    double maxScore = Double.NEGATIVE_INFINITY;
    int nbestId = 0;
    for (RichTranslation<IString,String> hypothesis : translations) {
      double loss = objective.score(translationId, references, hypothesis.translation);
      double modelScore = hypothesis.score;
      double score = modelScore + loss;
      // argmax
      if (score > maxScore) {
        d = hypothesis;
        dScore = modelScore;
        dCost = loss;
        dId = nbestId;
        maxScore = score;
      }
      ++nbestId;
    }

    assert d != null;
    return new Derivation(d, dScore, dCost, dId);
  }

  /**
   * Max model score + cost
   * @param objective 
   * 
   * @param translations
   * @param references 
   * @return
   */
  private Derivation getBestFearDerivation(SentenceLevelMetric<IString, String> objective, List<RichTranslation<IString,String>> translations, 
      List<Sequence<IString>> references, Derivation dHope,
      int translationId) {
    RichTranslation<IString,String> d = null;
    final double hopeCost = objective.score(translationId, references, dHope.hypothesis.translation);
    double dScore = 0.0;
    double dCost = 0.0;
    int dId = -1;
    double maxScore = Double.NEGATIVE_INFINITY;
    int nbestId = 0;
    for (RichTranslation<IString,String> hypothesis : translations) {
      double cost = objective.score(translationId, references, hypothesis.translation);
      double loss = hopeCost - cost;
      double modelScore = dHope.hypothesis.score - hypothesis.score;
      double score = loss - modelScore;
      // argmax
      if (score > maxScore && nbestId != dHope.nbestId) {
        d = hypothesis;
        dScore = hypothesis.score;
        dCost = cost;
        maxScore = score;
        dId = nbestId;
      }
      ++nbestId;
    }

    if (d == null) {
      // Logger is threadsafe. No worries.
      logger.warning("No fear derivation for: " + translationId);
    }
    
    return d == null ? dHope : new Derivation(d, dScore, dCost, dId);
  }


  private static class Derivation {
    public RichTranslation<IString,String> hypothesis;
    public double score;
    public double cost;
    public int nbestId;

    public Derivation(RichTranslation<IString,String> hypothesis, 
        double score, double cost, int nbestId) {
      this.hypothesis = hypothesis;
      this.score = score;
      this.cost = cost;
      this.nbestId = nbestId;
    }

    @Override
    public String toString() {
      return String.format("Cost: %.4f Score: %.4f || %s", cost, score, hypothesis.nbestToMosesString(nbestId));
    }
  }
}
