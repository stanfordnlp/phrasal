package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;

/**
 * Factory for sentence-level scoring metrics.
 * 
 * TODO(spenceg) Make the string specifications static final string constants.
 * 
 * @author Spence Green
 *
 */
public final class SentenceLevelMetricFactory {
  
  private static final int DEFAULT_ORDER = 4;
  
  private SentenceLevelMetricFactory() {}
  
  /**
   * Return the corresponding corpus-level specification from
   * <code>CorpusLevelMetricFactory</code>.
   * 
   * TODO(spenceg): These string constants should be static final variables
   * in CorpusLevelMetricFactory.
   * 
   * @param scoreMetricStr Sentence-level metric specification.
   * @return
   */
  public static String sentenceLevelToCorpusLevel(String scoreMetricStr) {
    if (scoreMetricStr.equals("bleu-smooth")) {
      return "bleu";

    } else if (scoreMetricStr.equals("bleu-smooth-unscaled")) {
      return "bleu";
      
    } else if (scoreMetricStr.equals("bleu-nakov")) {
      return "bleu";
    
    } else if (scoreMetricStr.equals("bleu-nakov-unscaled")) {
      return "bleu";
    
    } else if (scoreMetricStr.equals("bleu-chiang")) {
      return "bleu";

    } else if (scoreMetricStr.equals("bleu-cherry")) {
      return "bleu";
    
    } else if (scoreMetricStr.equals("terp")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("2bleu-terp")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleu-terp")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleu-2terp")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleusmooth-2terp")) {
      return "bleu-2terp";
    
    } else if (scoreMetricStr.equals("bleuX2terp")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleuXterp")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleu-2fastterp")) {
      return "bleu-2terp";
      
    } else {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    }
  }
  
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
