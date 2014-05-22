package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Corpus-level evaluation metrics.
 * 
 * @author Spence Green
 *
 */
public final class CorpusLevelMetricFactory {

  private CorpusLevelMetricFactory() {}

  /**
   * Return an instance of a corpus-level evaluation metric.
   * 
   * @param evalMetric String specifying the metric
   * @param references References set
   * @return
   */
  @SuppressWarnings("unchecked")
  public static AbstractMetric<IString,String> newMetric(String evalMetric,  
      List<List<Sequence<IString>>> references) { 

    AbstractMetric<IString,String> emetric = null;

    if (evalMetric.equals("smoothbleu")) {
      return new BLEUMetric<IString,String>(references, true);

    } else if (evalMetric.equals("bleu:3-2terp")) {
      int BLEUOrder = 3;
      double terW = 2.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, BLEUOrder), new TERpMetric<IString,String>(references));

    } else if (evalMetric.equals("bleu:3-terp")) {
      int BLEUOrder = 3;
      double terW = 1.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references, BLEUOrder), 
          new TERpMetric<IString, String>(references));

    } else if (evalMetric.equals("terp")) {
      emetric = new TERpMetric<IString, String>(references);

    } else if (evalMetric.equals("terpa")) {
      emetric = new TERpMetric<IString, String>(references, false, true);

    } else if (evalMetric.equals("ter")) {
      emetric = new TERMetric<IString,String>(references);

    } else if (evalMetric.equals("bleu")) {
      emetric = new BLEUMetric<IString, String>(references);

    } else if (evalMetric.equals("nist")) {
      emetric = new NISTMetric<IString, String>(references);

    } else if (evalMetric.startsWith("bleu-2terp")) {
      double terW = 2.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references));

    } else if (evalMetric.startsWith("bleu-2terpa")) {
      double terW = 2.0;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references, false, true));

    } else if (evalMetric.equals("(ter-bleu)/2")) {
      AbstractTERMetric<IString, String> termetric = new TERMetric<IString, String>(references);
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          0.5, 0.5 }, termetric, new BLEUMetric<IString, String>(references));

    } else if (evalMetric.startsWith("bleu-ter")) {
      double terW = 1.0;
      AbstractTERMetric<IString, String> termetric = new TERMetric<IString, String>(references);
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, terW }, new BLEUMetric<IString, String>(references),
          termetric);

    } else if (evalMetric.equals("wer")) {
      emetric = new WERMetric<IString, String>(references);

    } else if (evalMetric.equals("per")) {
      emetric = new PERMetric<IString, String>(references);

    } else {
      throw new UnsupportedOperationException(String.format(
          "Unrecognized metric: %s%n", evalMetric));
    }
    return emetric;
  }
}
