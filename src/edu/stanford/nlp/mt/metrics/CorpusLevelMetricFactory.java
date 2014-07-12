package edu.stanford.nlp.mt.metrics;

import java.util.List;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Corpus-level evaluation metrics.
 * 
 * TODO(spenceg) Make the string specifications static final string constants.
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
      emetric = new BLEUMetric<IString,String>(references, true);

    } else if (evalMetric.equals("bleu:3-2ter")) {
      int BLEUOrder = 3;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 2.0 }, new BLEUMetric<IString, String>(references, BLEUOrder), new TERpMetric<IString,String>(references));

    } else if (evalMetric.equals("bleu:3-ter")) {
      int BLEUOrder = 3;
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 1.0 }, new BLEUMetric<IString, String>(references, BLEUOrder), 
          new TERpMetric<IString, String>(references));

    } else if (evalMetric.equals("ter")) {
      emetric = new TERpMetric<IString, String>(references);

    } else if (evalMetric.equals("terpa")) {
      emetric = new TERpMetric<IString, String>(references, false, true);

    } else if (evalMetric.equals("bleu")) {
      emetric = new BLEUMetric<IString, String>(references);

    } else if (evalMetric.equals("nist")) {
      emetric = new NISTMetric<IString, String>(references);

    } else if (evalMetric.startsWith("bleu-ter")) {
        emetric = new LinearCombinationMetric<IString, String>(new double[] {
            1.0, 1.0 }, new BLEUMetric<IString, String>(references),
            new TERpMetric<IString, String>(references));
    
    } else if (evalMetric.startsWith("2bleu-ter")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          2.0, 1.0 }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references));
      
    } else if (evalMetric.startsWith("bleu-2ter")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 2.0 }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references));

    } else if (evalMetric.startsWith("bleu-2terpa")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 2.0 }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references, false, true));

    } else if (evalMetric.equals("bleu-ter/2")) {
      TERpMetric<IString, String> termetric = new TERpMetric<IString, String>(references);
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          0.5, 0.5 }, termetric, new BLEUMetric<IString, String>(references));

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
