package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Iterator;
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
 * Expected BLEU (Och 2003, Cherry and Foster 2012)
 * 
 * Optimizes: E_p_w(y|x) Loss(y) latex: \mathbb{E}_{p_\theta(y|x)} \ell (y)
 * 
 * The loss function, \ell, can be any machine translation evaluation metric 
 * that operates over individual translations such as smooth BLEU or TER.
 * 
 * The derivative of this objective is given by:
 * 
 * dL/dw_i = E_p_w(y|x) [Loss(y) * f_i(x,y)] - 
 *               E_p_w(y|x) [Loss(y)] *  E_p_w(y|x) [f_i(x,y)]
 * 
 * Latex:     \mathbb{E}_{p_\theta(y|x)} (\ell (y) f_i(x,y)) - 
 *              \mathbb{E}_{p_\theta(y|x)} \ell (y) \mathbb{E}_{p_\theta(y|x)}  f_i(x,y)
 *              
 * @author Daniel Cer 
 *
 */
public class ExpectedBLEUOptimizer2 extends AbstractOnlineOptimizer {

  // Testing options
  public static final boolean LENGTH_SCALE = System.getProperty("ebLengthScale") != null;
  public static final boolean CONVEX_TRANSFORM = System.getProperty("ebConvexTransform") != null;
  public static final double TEMPERATURE = System.getProperty("ebTemperature") == null ? 1.0 : 
    Double.parseDouble(System.getProperty("ebTemperature"));
  
  static public boolean VERBOSE = false;

  private static final int INITIAL_CAPACITY = 5000;
  
  public ExpectedBLEUOptimizer2(int tuneSetSize, int expectedNumFeatures, String[] args) {
    super(tuneSetSize, expectedNumFeatures, args);
  }	

  private double logZ(
      List<RichTranslation<IString, String>> translations,
      Counter<String> wts) {
    double scores[] = new double[translations.size()];
    int max_i = 0;

    Iterator<RichTranslation<IString, String>> iter = translations
        .iterator();
    for (int i = 0; iter.hasNext(); i++) {
      ScoredFeaturizedTranslation<IString, String> trans = iter.next();
      scores[i] = OptimizerUtils.scoreTranslation(wts, trans) / TEMPERATURE;
      if (scores[i] > scores[max_i])
        max_i = i;
    }

    double expSum = 0;
    for (int i = 0; i < scores.length; i++) {
      expSum += Math.exp(scores[i] - scores[max_i]);
    }

    return scores[max_i] + Math.log(expSum);
  }

  @Override
  public Counter<String> getUnregularizedGradient(Counter<String> weights,
      Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, double[] referenceWeights,
      SentenceLevelMetric<IString, String> scoreMetric) {
    assert weights != null;
    assert references.size() > 0;
    assert scoreMetric != null;
    
    Counter<String> expectedLossF = new ClassicCounter<String>(INITIAL_CAPACITY);
    Counter<String> expectedF = new ClassicCounter<String>(INITIAL_CAPACITY);
    double expectedLoss = 0;

    int minLength = Integer.MAX_VALUE;
    if (LENGTH_SCALE) {
      for (Sequence<IString> sentence : references) {
        if (sentence.size() < minLength) {
          minLength = sentence.size();
        }
      }
    }
    
    double logZ = logZ(translations, weights);
    double argmaxScore = Double.NEGATIVE_INFINITY;
    double argmaxP = 0;
    double argmaxEval = 0;
    for (RichTranslation<IString,String> trans: translations) {
      double score =  OptimizerUtils.scoreTranslation(weights, trans) / TEMPERATURE;
      double logP = score - logZ;
      double p = Math.exp(logP);
      double eval = scoreMetric.score(sourceId, source, references, trans.translation);
      // System.err.printf("score: %.3f p: %.3f eval %.3f\n", score, p, eval);
      
      // Apply a convex function and then scale up.
      if (CONVEX_TRANSFORM) {
        eval = Math.pow(eval, 2.0);
      }
      if (LENGTH_SCALE) {
        eval *= minLength;
      }
      
      double Eeval = p*eval;
      expectedLoss += Eeval;
      for (FeatureValue<String> feat : trans.features) {
        double EfeatEval = Eeval*feat.value;
        double Efeat = p*feat.value;
        expectedLossF.incrementCount(feat.name, EfeatEval);
        expectedF.incrementCount(feat.name, Efeat);
      }

      if (score > argmaxScore) {
        argmaxScore = score;
        argmaxP = p;
        argmaxEval = eval; 
      }
    }

    // spenceg: These memory copies are redundant
//    Counter<String> expectedLossExpectedF = new ClassicCounter<String>(expectedF);
//    Counters.multiplyInPlace(expectedLossExpectedF, expectedLoss);
//    Counter<String> gradient = new ClassicCounter<String>(expectedLossF);
//    Counters.subtractInPlace(gradient, expectedLossExpectedF);

    Counters.multiplyInPlace(expectedF, expectedLoss);
    Counters.subtractInPlace(expectedLossF, expectedF);
    Counter<String> gradient = expectedLossF;
    
    if (VERBOSE) {
      System.err.println("======================");
      System.err.printf("Argmax score: %.3f P: %.3f Eval: %.3f\n", argmaxScore, argmaxP, argmaxEval);
      System.err.println("Gradient: ");
      System.err.println(gradient);
      System.err.println();
    }

    Counters.multiplyInPlace(gradient, -1);
    return gradient;
  }
}
