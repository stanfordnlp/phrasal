package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * 1-best MIRA training with hope/fear translations. See Watanabe et al. (2007), 
 * Chiang (2012), and Eidelman (2012).
 * 
 * 
 * TODO:
 *   * We need to generate a new k-best list for every sentence that we decode.
 *  
 * @author Spence Green
 */
public class MIRAHopeFearOptimizer implements OnlineOptimizer<IString,String> {

  //  private static final String DEBUG_PROPERTY = "MIRAHopeFear";
  //  private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
  //      DEBUG_PROPERTY, "false"));
  private static final boolean DEBUG = true;

  /**
   * Default aggressiveness parameter.
   */
  public static final double DEFAULT_C = 0.01;

  private final double C;


  public MIRAHopeFearOptimizer(double C) {
    this.C = C;
  }

  public MIRAHopeFearOptimizer(String... args) {
    C = (args.length == 1) ? Double.parseDouble(args[0]) : DEFAULT_C;
  }

  @Override
  public Counter<String> update(Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references,
      SentenceLevelMetric<IString, String> objective, Counter<String> weights) {
    
    System.err.printf("1-best MIRA optimization with C: %e%n", C);
    
    Counter<String> wts = new ClassicCounter<String>(weights);
    Derivation dHope = getBestHopeDerivation(objective, translations, references, sourceId);
    Derivation dFear = getBestFearDerivation(objective, translations, references, dHope.hypothesis, sourceId);
    
    // TODO(spenceg): Update according to eq.50, eq.52 in Crammer et al. (2006)
    // Should email Eidelman to confirm the signs of these values
    
    double margin = dHope.score - dFear.score;
    double loss = dHope.cost - dFear.cost;
    if (DEBUG) {
      System.err.printf("MIRAHopeFear: example %d margin %.4f loss %.4f loss %.4f%n", sourceId,
          margin, loss);
    }
    if (margin > loss) {
      // Compute the PA-I update, which is the loss divided by the 
      // squared norm of the differences between the feature vectors
      Counter<String> featureDiff = getFeatureDiff(dHope,dFear);
      double norm = Counters.L2Norm(featureDiff);
      double d = Math.min(C, loss / (norm*norm));
      if (DEBUG) {
        System.err.printf("MIRAHopeFear: PA-I update loss %.4f norm %.4f d %.4f%n", loss, norm, d);
        System.err.println("MIRAHopeFear: feature difference\n" + featureDiff.toString());
      }
      Counters.multiplyInPlace(featureDiff, d);
      Counters.addInPlace(wts, featureDiff);
    }
    return wts;
  }

  /**
   * Compute a vector containing the differences in feature values of the hope and fear derivations.
   * 
   * Note: these features are in log space?
   * 
   * @param dHope
   * @param dFear
   * @return
   */
  private Counter<String> getFeatureDiff(Derivation dHope, Derivation dFear) {
    Counter<String> featureDiff = OptimizerUtils.featureValueCollectionToCounter(dHope.hypothesis.features);
    assert dFear.hypothesis.features.size() == featureDiff.size();
    for (FeatureValue<String> fearFeature : dFear.hypothesis.features) {
      assert featureDiff.containsKey(fearFeature.name);
      double hopeFeature = featureDiff.getCount(fearFeature.name);
      if (DEBUG) {
        System.err.printf("featureDiff (%s): hope %.4f fear %.4f%n", fearFeature.name, hopeFeature, fearFeature.value);
      }
      double diff = hopeFeature - fearFeature.value;
      featureDiff.setCount(fearFeature.name, diff);
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
      double score = modelScore - loss;
      if (DEBUG) {
        System.err.printf("MIRAHopeFear (hope): Loss %.4f Model %.4f Score %.4f%n", loss, modelScore, score);
        System.err.println(hypothesis.toString());
      }
      // argmax
      if (score > maxScore) {
        if (DEBUG) System.err.println("HopeDerivation: Selected derivation " + hypothesis.toString());
        d = hypothesis;
        dScore = modelScore;
        dLoss = loss;
        maxScore = score;
      }
    }

    assert d != null;
    Derivation dHope = new Derivation(d, dScore, dLoss);
    if (DEBUG) {
      System.err.println("MIRAHopeFear: Hope derivation > " + dHope.toString());
    }
    return dHope;
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
      if (DEBUG) {
        System.err.printf("FearDerivation (hope): cost %.4f score %.4f%n", hopeCost, hopeHypothesis.score);
        System.err.printf("FearDerivation (cand): cost %.4f score %.4f%n", cost, hypothesis.score);
        System.err.printf("FearDerivation (marg): score %.4f%n", score);
      }
      // argmax
      if (score > maxScore) {
        if (DEBUG) System.err.println("FearDerivation: Selected derivation " + hypothesis.toString());
        d = hypothesis;
        dScore = Math.exp(hypothesis.score);
        dLoss = cost;
        maxScore = score;
      }
    }

    assert d != null;
    Derivation dFear = new Derivation(d, dScore, dLoss);
    if (DEBUG) {
      System.err.println("MIRAHopeFear: Fear derivation > " + dFear.toString());
    }
    return dFear;
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
