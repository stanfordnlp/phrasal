package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
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
public class MIRAHopeFearOptimizer implements OnlineOptimizer<IString,String> {

  /**
   * Default aggressiveness parameter.
   */
  public static final double DEFAULT_C = 0.01;

  private final double C;
  
  private final Logger logger;

  public MIRAHopeFearOptimizer(double C) {
    this.C = C;
    logger = Logger.getLogger(MIRAHopeFearOptimizer.class.getCanonicalName());
    OnlineTuner.attach(logger);
    logger.info(String.format("1-best MIRA optimization with C: %e", C));
  }

  public MIRAHopeFearOptimizer(String... args) {
    C = (args == null || args.length != 1) ? DEFAULT_C : Double.parseDouble(args[0]);
    logger = Logger.getLogger(MIRAHopeFearOptimizer.class.getCanonicalName());
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
      SentenceLevelMetric<IString, String> objective, Counter<String> weights) {
    
    Counter<String> wts = new ClassicCounter<String>(weights);
    
    // The "correct" derivation (Crammer et al. (2006) fig.2)
    Derivation dHope = getBestHopeDerivation(objective, translations, references, sourceId);
    logger.info("Hope derivation: " + dHope.toString());
    
    // The "max-loss" derivation (Crammer et al. (2006) fig.2)
    Derivation dFear = getBestFearDerivation(objective, translations, references, dHope.hypothesis, sourceId);
    logger.info("Fear derivation: " + dFear.toString());
    
    double margin = dFear.score - dHope.score;
    // TODO(spenceg): Crammer takes the square root of the cost
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
  private Counter<String> getFeatureDiff(Derivation d1, Derivation d2) {
    Counter<String> featureDiff = OptimizerUtils.featureValueCollectionToCounter(d1.hypothesis.features);
    assert d2.hypothesis.features.size() == featureDiff.size();
    for (FeatureValue<String> d2Feature : d2.hypothesis.features) {
      assert featureDiff.containsKey(d2Feature.name);
      double d1Value = featureDiff.getCount(d2Feature.name);
      double diff = d1Value - d2Feature.value;
      featureDiff.setCount(d2Feature.name, diff);
    }
    return featureDiff;
  }

  /**
   * Max model score - cost
   * @param objective 
   * 
   * @param translations
   * @param references 
   * @return
   */
  private Derivation getBestHopeDerivation(SentenceLevelMetric<IString, String> objective, List<RichTranslation<IString,String>> translations,
      List<Sequence<IString>> references, int nbestId) {

    ScoredFeaturizedTranslation<IString,String> d = null;
    double dScore = 0.0;
    double dLoss = 0.0;
    double maxScore = Double.NEGATIVE_INFINITY;
    for (ScoredFeaturizedTranslation<IString,String> hypothesis : translations) {
      double loss = objective.score(nbestId, references, hypothesis.translation);
      double modelScore = hypothesis.score;
      double score = modelScore + loss;
      // argmax
      if (score > maxScore) {
        d = hypothesis;
        dScore = modelScore;
        dLoss = loss;
        maxScore = score;
      }
    }

    assert d != null;
    return new Derivation(d, dScore, dLoss);
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
      List<Sequence<IString>> references, ScoredFeaturizedTranslation<IString,String> hopeHypothesis,
      int nbestId) {
    ScoredFeaturizedTranslation<IString,String> d = null;
    final double hopeCost = objective.score(nbestId, references, hopeHypothesis.translation);
    double dScore = 0.0;
    double dLoss = 0.0;
    double maxScore = Double.NEGATIVE_INFINITY;
    for (ScoredFeaturizedTranslation<IString,String> hypothesis : translations) {
      double cost = objective.score(nbestId, references, hypothesis.translation);
      double loss = hopeCost - cost;
      double modelScore = hopeHypothesis.score - hypothesis.score;
      double score = loss - modelScore;
      // argmax
      if (score > maxScore && score != 0.0) {
        d = hypothesis;
        dScore = hypothesis.score;
        dLoss = cost;
        maxScore = score;
      }
    }

    assert d != null;
    return new Derivation(d, dScore, dLoss);
  }


  private static class Derivation {
    public ScoredFeaturizedTranslation<IString,String> hypothesis;
    public double score;
    public double cost;

    public Derivation(ScoredFeaturizedTranslation<IString,String> hypothesis, 
        double score, double cost) {
      this.hypothesis = hypothesis;
      this.score = score;
      this.cost = cost;
    }

    @Override
    public String toString() {
      return String.format("Cost: %.4f Score: %.4f%n%s", cost, score, hypothesis.toString());
    }
  }
}
