package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;


/**
 * Cross-entropy objective function.             
 * 
 * @author Spence Green
 *
 */
public class CrossEntropyOptimizer extends AbstractOnlineOptimizer {

  private static final int INITIAL_CAPACITY = 2000;

  private static final int DEFAULT_TOPK = 40;
  private int topK = DEFAULT_TOPK;
  private boolean printDebugOutput = false;

  private static final Logger logger = LogManager.getLogger(CrossEntropyOptimizer.class.getName());
  
  /**
   * Constructor.
   * 
   * @param tuneSetSize
   * @param expectedNumFeatures
   * @param args
   */
  public CrossEntropyOptimizer(int tuneSetSize, int expectedNumFeatures, String[] args) {
    super(tuneSetSize, expectedNumFeatures, args);
    
    // Process optimizer specific arguments
    for (String arg : args) {
      if (arg.startsWith("ceTopk")) {
        String[] fields = arg.split("=");
        if (fields.length != 2) throw new RuntimeException("Invalid argument: " + arg);
        topK = Integer.valueOf(args[1]);
        
      } else if (arg.startsWith("ceDebug")) {
        String[] fields = arg.split("=");
        if (fields.length != 2) throw new RuntimeException("Invalid argument: " + arg);
        printDebugOutput = Boolean.valueOf(args[1]);
      }
    }
    logger.info("Top K: {}", topK);
    logger.info("Debug mode: {}", printDebugOutput);
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
      System.err.printf("NULL GRADIENT FOR source id %d%n", sourceId);
      return new ClassicCounter<String>();
    }
    
    // Compute the score for everything in the n-best list
    // Don't know where the top K are yet.
    List<GoldScoredTranslation> metricScoredList = new ArrayList<>(translations.size());
    double qNormalizer = 0.0;
    
    for (int i = 0, sz = translations.size(); i < sz; ++i) {
      RichTranslation<IString,String> translation = translations.get(i);
      qNormalizer += Math.exp(translation.score);
      double labelScore = scoreMetric.score(sourceId, source, references, translation.translation);
      metricScoredList.add(new GoldScoredTranslation(translation, labelScore, i));
    }
    Collections.sort(metricScoredList);

    double pNormalizer = 0.0;
    double lastScore = Double.NEGATIVE_INFINITY;
    int rank = 0;
    int id = 1;
    Map<Long,GoldScoredTranslation> items = new HashMap<>();
    for (GoldScoredTranslation g : metricScoredList) {
      if (g.goldScore != lastScore) {
        rank = id;
      }
      lastScore = g.goldScore;
      if (rank > topK) break;
      g.goldScore = g.goldScore / Math.log(rank + 1);
      pNormalizer += g.goldScore;
      items.put(g.t.latticeSourceId, g);
      ++id;
    }

    if (pNormalizer == 0.0) {
      System.err.printf("NULL GRADIENT FOR source id %d due to 0 BLEU score%n", sourceId);
      return new ClassicCounter<String>();
    }
    
    if (printDebugOutput) {
      System.err.printf("%d #items %d max: %.3f / %d%n", sourceId, 
          items.size(), metricScoredList.get(0).goldScore, metricScoredList.get(0).t.latticeSourceId);
    }
    
    Counter<String> gradient = new ClassicCounter<String>(INITIAL_CAPACITY);
    double logQNormalizer = Math.log(qNormalizer);
    for (RichTranslation<IString, String> translation : translations) {
      double p = items.containsKey(translation.latticeSourceId) ? 
          items.get(translation.latticeSourceId).goldScore / pNormalizer : 0.0;
      double logQ = translation.score - logQNormalizer;
      assert ! Double.isNaN(p) : String.format("%d: %f %f", sourceId, items.get(translation.latticeSourceId).goldScore, pNormalizer);
      double diff = Math.exp(logQ) - p;
      if (diff == 0.0) continue;
      for (FeatureValue<String> f : translation.features) {
        double g = f.value * diff;
        assert ! Double.isNaN(g) : String.format("%s %f %f", f.name, f.value, diff);
        gradient.incrementCount(f.name, g);
      }
    }
    
    return gradient;
  }
  
  private static class GoldScoredTranslation implements Comparable<GoldScoredTranslation> {
    public final RichTranslation<IString,String> t;
    public double goldScore;
    public int nbestRank;
    public GoldScoredTranslation(RichTranslation<IString,String> t, double goldScore, int nbestRank) {
      this.t = t;
      this.goldScore = goldScore;
      this.nbestRank = nbestRank;
    }
    
    @Override
    public String toString() {
      return String.format("id: %d score: %.4f", nbestRank, goldScore);
    }
    
    @Override
    public int compareTo(GoldScoredTranslation o) {
      int scoreCmp = (int) Math.signum(o.goldScore - this.goldScore);
      return scoreCmp == 0 ? nbestRank - o.nbestRank : scoreCmp;
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
        return this.t == o.t && this.nbestRank == o.nbestRank;
      }
    }
  }
  
}
