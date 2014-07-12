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

    } else if (scoreMetricStr.equals("tergain")) {
      return "ter";
      
    } else if (scoreMetricStr.equals("ter")) {
      return scoreMetricStr;
      
    } else if (scoreMetricStr.equals("ter")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("2bleu-ter")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleu-ter")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleu-2ter")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleus-2ter")) {
      return "bleu-2ter";
    
    } else if (scoreMetricStr.equals("bleu-ter/2") || scoreMetricStr.equals("bleus-ter/2")) {
      return "bleu-ter/2";
    
    } else if (scoreMetricStr.equals("bleuX2ter")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleuXter")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleu-2fastter")) {
      return "bleu-2ter";
      
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
    
    } else if (scoreMetricStr.equals("tergain")) {
      return new SLTERGain<IString,String>();
      
    } else if (scoreMetricStr.equals("ter")) {
      return new SLTERMetric<IString,String>();
    
    } else if (scoreMetricStr.equals("2bleu-ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{2.0, 1.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 1.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleus-2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>());
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-ter/2")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{0.5, 0.5}, metrics);
    
    } else if (scoreMetricStr.equals("bleus-ter/2")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>());
      metrics.add(new SLTERMetric<IString,String>());
      return new SLLinearCombinationMetric<IString,String>(
        new double[]{0.5, 0.5}, metrics);
      
    } else if (scoreMetricStr.equals("bleuX2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLGeometricCombinationMetric<IString,String>(
        new double[]{1.0, 2.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleuXter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>());
      return new SLGeometricCombinationMetric<IString,String>(
        new double[]{1.0, 1.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-2fastter")) {
      List<SentenceLevelMetric<IString,String>> metrics = Generics.newArrayList(2);
      metrics.add(new BLEUGain<IString,String>(true));
      metrics.add(new SLTERMetric<IString,String>(5));
      return new SLLinearCombinationMetric<IString,String>(new double[]{1.0, 2.0}, metrics);
    
    } else {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    }
  }
}
