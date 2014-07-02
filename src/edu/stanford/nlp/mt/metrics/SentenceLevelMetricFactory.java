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
    switch (scoreMetricStr) {
      case "bleu-smooth":
        return "bleu";

      case "bleu-smooth-unscaled":
        return "bleu";

      case "bleu-nakov":
        return "bleu";

      case "bleu-nakov-unscaled":
        return "bleu";

      case "bleu-chiang":
        return "bleu";

      case "bleu-cherry":
        return "bleu";

      case "tergain":
        return "terp";

      case "terp":
        return scoreMetricStr;

      case "2bleu-terp":
        return scoreMetricStr;

      case "bleu-terp":
        return scoreMetricStr;

      case "bleu-2terp":
        return scoreMetricStr;

      case "bleus-2terp":
        return "bleu-2terp";

      case "bleu-terp/2":
      case "bleus-terp/2":
        return "bleu-terp/2";

      case "bleuX2terp":
        throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);

      case "bleuXterp":
        throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);

      case "bleu-2fastterp":
        return "bleu-2terp";

      default:
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

    switch (scoreMetricStr) {
      case "bleu-smooth":
        // Lin and Och smoothed BLEU (BLEU+1)
        return new BLEUGain<IString, String>();

      case "bleu-smooth-unscaled":
        // Nakov's extensions to BLEU+1
        return new BLEUGain<IString, String>(DEFAULT_ORDER, false, false);

      case "bleu-nakov":
        // Nakov's extensions to BLEU+1
        return new BLEUGain<IString, String>(true);

      case "bleu-nakov-unscaled":
        // Nakov's extensions to BLEU+1
        return new BLEUGain<IString, String>(DEFAULT_ORDER, true, false);

      case "bleu-chiang":
        // Chiang's oracle document and exponential decay
        return new BLEUOracleCost<IString, String>(DEFAULT_ORDER, false);

      case "bleu-cherry":
        // Cherry and Foster (2012)
        return new BLEUOracleCost<IString, String>(DEFAULT_ORDER, true);

      case "tergain":
        return new SLTERGain<IString, String>();

      case "terp":
        return new SLTERpMetric<IString, String>();

      case "2bleu-terp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{2.0, 1.0}, metrics);

      }
      case "bleu-terp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{1.0, 1.0}, metrics);

      }
      case "bleu-2terp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{1.0, 2.0}, metrics);

      }
      case "bleus-2terp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>());
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{1.0, 2.0}, metrics);

      }
      case "bleu-terp/2": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{0.5, 0.5}, metrics);

      }
      case "bleus-terp/2": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>());
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLLinearCombinationMetric<IString, String>(
            new double[]{0.5, 0.5}, metrics);

      }
      case "bleuX2terp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLGeometricCombinationMetric<IString, String>(
            new double[]{1.0, 2.0}, new boolean[]{false, true}, metrics);

      }
      case "bleuXterp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>());
        return new SLGeometricCombinationMetric<IString, String>(
            new double[]{1.0, 1.0}, new boolean[]{false, true}, metrics);

      }
      case "bleu-2fastterp": {
        List<SentenceLevelMetric<IString, String>> metrics = Generics.newArrayList(2);
        metrics.add(new BLEUGain<IString, String>(true));
        metrics.add(new SLTERpMetric<IString, String>(5));
        return new SLLinearCombinationMetric<IString, String>(new double[]{1.0, 2.0}, metrics);

      }
      default:
        throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    }
  }
}
