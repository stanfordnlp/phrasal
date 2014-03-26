package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * Cross-entropy objective function.             
 * 
 * @author Spence Green
 *
 */
public class CrossEntropyOptimizer extends AbstractOnlineOptimizer {

  private static final int K_BEST = 5;
  
  private static final int INITIAL_CAPACITY = 2000;

  private static final boolean DEBUG = true;
  
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
    
    if (translations.size() == 0) {
      System.err.printf("WSGDEBUG: NULL GRADIENT FOR source id %d%n", sourceId);
      return new ClassicCounter<String>();
    }
    
    // Get the minimum reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<IString> ref : references) {
      if (ref.size() < minLength) {
        minLength = ref.size();
      }
    }
    
    // Compute the model and the label distributions
    List<GoldScoredTranslation> scoredList = Generics.newArrayList(translations.size());
    double qNormalizer = 0.0;
    for (RichTranslation<IString,String> translation : translations) {
      qNormalizer += Math.exp(translation.score);
      double labelScore = scoreMetric.score(sourceId, source, references, translation.translation);
      double scaledScore = scaleScore(labelScore, minLength);
      scoredList.add(new GoldScoredTranslation(translation, scaledScore));
    }
    Collections.sort(scoredList);

    double pNormalizer = 0.0;
    double lastScore = 0.0;
    int rank = 0;
    int id = 1;
    Map<Long,GoldScoredTranslation> items = Generics.newHashMap();
    for (GoldScoredTranslation g : scoredList) {
      if (g.goldScore != lastScore) {
        rank = id;
      }
      lastScore = g.goldScore;
      if (rank > K_BEST) break;
      g.goldScore = g.goldScore / Math.log(rank + 1);
      pNormalizer += g.goldScore;
      items.put(g.t.latticeSourceId, g);
      ++id;
    }
    
    if (DEBUG) {
      System.err.printf("%d #items %d max: %.3f / %d%n", sourceId, 
          items.size(), scoredList.get(0).goldScore, scoredList.get(0).t.latticeSourceId);
    }
    
    Counter<String> gradient = new ClassicCounter<String>(INITIAL_CAPACITY);
    for (RichTranslation<IString, String> translation : translations) {
      double p = items.containsKey(translation.latticeSourceId) ? 
          items.get(translation.latticeSourceId).goldScore / pNormalizer : 0.0;
      double q = Math.exp(translation.score) / qNormalizer;
      double diff = q - p;
      if (diff == 0.0) continue;
      for (FeatureValue<String> f : translation.features) {
        double g = f.value * diff;
        assert ! Double.isNaN(g) : String.format("%s %f %f", f.name, f.value, diff);
        gradient.incrementCount(f.name, g);
      }
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
  private double scaleScore(double labelScore, int minRefLength) {
    // Convex transform
    labelScore *= labelScore;
    // Scale by min reference length
    return labelScore * minRefLength;
  }
  
  
  private static class GoldScoredTranslation implements Comparable<GoldScoredTranslation> {
    public final RichTranslation<IString,String> t;
    public double goldScore;
    public GoldScoredTranslation(RichTranslation<IString,String> t, double goldScore) {
      this.t = t;
      this.goldScore = goldScore;
    }
    
    @Override
    public String toString() {
      return String.format("id: %d score: %.4f", (int) t.latticeSourceId, goldScore);
    }
    
    @Override
    public int compareTo(GoldScoredTranslation o) {
      return (int) Math.signum(o.goldScore - this.goldScore);
    }
    
    @Override
    public int hashCode() { 
      return (int) this.t.latticeSourceId; 
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof GoldScoredTranslation)) {
        return false;
      } else {
        GoldScoredTranslation o = (GoldScoredTranslation) other;
        return this.t == o.t;
      }
    }
  }
  
}
