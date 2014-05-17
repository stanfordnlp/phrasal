package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;

/**
 * Factory for sentence-level scoring metrics.
 * 
 * @author Spence Green
 *
 */
public final class SentenceLevelMetricFactory {
  
  private static final int DEFAULT_ORDER = 4;
  
  private SentenceLevelMetricFactory() {}
  
  /**
   * Load a scoring metric from a string key.
   * 
   * @param scoreMetricStr
   * @param scoreMetricOpts 
   * @return
   */
  public static SentenceLevelMetric<IString, String> getMetric(
      String scoreMetricStr, String[] scoreMetricOpts) {
    
    if (scoreMetricStr.equals("bleu-smooth")) {
      // Lin and Och smoothed BLEU (BLEU+1)
      return new BLEUGain<IString,String>();

    } else if (scoreMetricStr.equals("bleu-smooth-unscaled")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<IString,String>(DEFAULT_ORDER, false, false);
    
    } else if (scoreMetricStr.equals("bleu-nakov")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<IString,String>(true);
    
    } else if (scoreMetricStr.equals("bleu-nakov-unscaled")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<IString,String>(DEFAULT_ORDER, true, false);
    
    } else if (scoreMetricStr.equals("bleu-chiang")) {
      // Chiang's oracle document and exponential decay
      return new BLEUOracleCost<IString,String>(DEFAULT_ORDER, false);

    } else if (scoreMetricStr.equals("bleu-cherry")) {
      // Cherry and Foster (2012)
      return new BLEUOracleCost<IString,String>(DEFAULT_ORDER, true);
    
    } else if (scoreMetricStr.equals("tergain")) {
      return new SLTERGain<IString,String>();
      
    } else if (scoreMetricStr.equals("terp")) {
      return new SLTERpMetric<IString,String>();
    
    } else if (scoreMetricStr.equals("2bleu-terp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{2.0, 1.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-terp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 1.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-2terp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-s-2terp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>());
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleuX2terp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLGeometricCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleuXterp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>());
      return new SLGeometricCombinationMetric<IString,String>(
        new double[]{1.0, 1.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-2fastterp")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERpMetric<IString,String>(5));
      return new SLLinearCombinationMetric<IString,String>(new double[]{1.0, 2.0}, metrics);
    
    } else {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    }
  }
}
