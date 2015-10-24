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
      emetric = new BLEUMetric<>(references, true);


    } else if (evalMetric.equals("bleu:3-2ter")) {
      int BLEUOrder = 3;
      emetric = new LinearCombinationMetric<>(new double[] {
          1.0, 2.0 }, new BLEUMetric<>(references, BLEUOrder), new TERpMetric<>(references));

    } else if (evalMetric.equals("bleu:3-ter")) {
      int BLEUOrder = 3;
      emetric = new LinearCombinationMetric<>(new double[] {
          1.0, 1.0 }, new BLEUMetric<>(references, BLEUOrder), 
          new TERpMetric<>(references));

    } else if (evalMetric.equals("ter")) {
      emetric = new TERpMetric<>(references);

    } else if (evalMetric.equals("terpa")) {
      emetric = new TERpMetric<>(references, false, true);

    } else if (evalMetric.equals("bleu")) {
      emetric = new BLEUMetric<>(references);

    } else if (evalMetric.equals("nist")) {
      emetric = new NISTMetric<>(references);

    } else if (evalMetric.equals("bleu-ter")) {
        emetric = new LinearCombinationMetric<>(new double[] {
            1.0, 1.0 }, new BLEUMetric<>(references),
            new TERpMetric<>(references));
    
    } else if (evalMetric.equals("2bleu-ter")) {
      emetric = new LinearCombinationMetric<>(new double[] {
          2.0, 1.0 }, new BLEUMetric<>(references),
          new TERpMetric<>(references));
      
    } else if (evalMetric.equals("bleu-2ter")) {
      emetric = new LinearCombinationMetric<>(new double[] {
          1.0, 2.0 }, new BLEUMetric<>(references),
          new TERpMetric<>(references));

    } else if (evalMetric.equals("bleu-2terpa")) {
      emetric = new LinearCombinationMetric<>(new double[] {
          1.0, 2.0 }, new BLEUMetric<>(references),
          new TERpMetric<>(references, false, true));

    } else if (evalMetric.equals("bleu-ter/2")) {
      TERpMetric<IString,String> termetric = new TERpMetric<>(references);
      emetric = new LinearCombinationMetric<>(new double[] {
          0.5, 0.5 }, termetric, new BLEUMetric<>(references));
		} else if (evalMetric.equals("wer")) {
      emetric = new WERMetric<>(references);

    } else if (evalMetric.equals("per")) {
      emetric = new PERMetric<>(references);
      
    } else if (evalMetric.equals("numPredictedWords")) {
      emetric = new NumPredictedWordsMetric<>(references);
    
    } else if (evalMetric.equals("nextPredictedWord")) {
      emetric = new NextPredictedWordMetric<>(references);
    
    } else if (evalMetric.equals("maxPredictedWords")) {
      emetric = new MaxPredictedWordsMetric<>(references);
    
    } else if (evalMetric.equals("bleu-prefix")) {
      emetric = new BLEUAfterPrefixMetric<String>(references);
    
    } else if (evalMetric.equals("bleup-nextw/2")) {
      emetric = new LinearCombinationMetric<>(new double[] {
          1.0, 1.0 }, new BLEUAfterPrefixMetric<String>(references),
          new NextPredictedWordMetric<>(references));
    
    } else if (evalMetric.equals("repetitionRate")) {
      emetric = new RepetitionRate<>();

    } else {
			throw new UnsupportedOperationException("Unrecognized metric: " + evalMetric);
    }
    return emetric;
  }	
}
