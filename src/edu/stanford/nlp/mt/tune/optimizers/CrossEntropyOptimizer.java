package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Cross-entropy objective function.             
 * 
 * @author Spence Green
 *
 */
public class CrossEntropyOptimizer extends AbstractOnlineOptimizer {

  // Testing options
  public static final boolean CONVEX_TRANSFORM = System.getProperty("ceConvexTransform") != null;
  
  private static final int INITIAL_CAPACITY = 5000;

  private static final boolean DEBUG = false;
  
  /**
   * Constructor.
   * 
   * @param tuneSetSize
   * @param expectedNumFeatures
   * @param args
   */
  public CrossEntropyOptimizer(int tuneSetSize, int expectedNumFeatures, String[] args) {
    super(tuneSetSize, expectedNumFeatures, args);
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
    
    // Get the minimum reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<IString> ref : references) {
      if (ref.size() < minLength) {
        minLength = ref.size();
      }
    }
    
    // Compute the model and the label distributions
    final int nbestListSize = translations.size();
    if (nbestListSize == 0) {
      System.err.printf("WSGDEBUG: NULL GRADIENT FOR source id %d%n", sourceId);
      return new ClassicCounter<String>();
    }
    double labelDistribution[] = new double[nbestListSize];
    double modelDistribution[] = new double[nbestListSize];
    
    for (int i = 0; i < nbestListSize; ++i) {
      RichTranslation<IString,String> translation = translations.get(i);
      modelDistribution[i] = Math.exp(translation.score);
      double labelScore = scoreMetric.score(sourceId, source, references, translation.translation);
      labelDistribution[i] = transformScore(translation, labelScore, minLength);
    }
    
    // DEBUG
//    double bleuScores[] = new double[nbestListSize];
//    System.arraycopy(labelDistribution, 0, bleuScores, 0, nbestListSize);
    
    ArrayMath.normalize(labelDistribution);
    ArrayMath.normalize(modelDistribution);

    Counter<String> gradient = new ClassicCounter<String>(INITIAL_CAPACITY);
    for (int i = 0; i < nbestListSize; ++i) {
      RichTranslation<IString,String> translation = translations.get(i);
      double p = labelDistribution[i];
      double q = modelDistribution[i];
      double diff = q - p;
      for (FeatureValue<String> f : translation.features) {
        double g = f.value * diff;
        gradient.incrementCount(f.name, g);
      }
    }
    
    // DEBUG
    if (DEBUG) {
//      double expBLEU = 0.0;
//      double crossEntropy = 0.0;  
//      for (int i = 0; i < nbestListSize; ++i) {
////        RichTranslation<IString,String> translation = translations.get(i);
//        expBLEU += (bleuScores[i] * modelDistribution[i]);
//        crossEntropy -= (labelDistribution[i] * Math.log(modelDistribution[i]));
//      }
//      System.err.printf("%d EB: %.3f  CE: %.3f%n", sourceId, expBLEU, crossEntropy);
    }
    return gradient;
  }

  /**
   * Transform the sentence level score.
   * 
   * @param translation
   * @param labelScore
   * @param minRefLength
   * @return
   */
  private double transformScore(RichTranslation<IString, String> translation,
      double labelScore, int minRefLength) {
    if (CONVEX_TRANSFORM) {
      labelScore *= labelScore;
    }
    return labelScore * minRefLength;
  }
}
