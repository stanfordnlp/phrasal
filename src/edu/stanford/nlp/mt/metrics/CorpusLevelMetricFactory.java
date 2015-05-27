package edu.stanford.nlp.mt.metrics;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

    } else if (evalMetric.equals("bleu-ter")) {
        emetric = new LinearCombinationMetric<IString, String>(new double[] {
            1.0, 1.0 }, new BLEUMetric<IString, String>(references),
            new TERpMetric<IString, String>(references));
    
    } else if (evalMetric.equals("2bleu-ter")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          2.0, 1.0 }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references));
      
    } else if (evalMetric.equals("bleu-2ter")) {
      emetric = new LinearCombinationMetric<IString, String>(new double[] {
          1.0, 2.0 }, new BLEUMetric<IString, String>(references),
          new TERpMetric<IString, String>(references));

    } else if (evalMetric.equals("bleu-2terpa")) {
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
      
    } else if (evalMetric.equals("numPredictedWords")) {
      emetric = new NumPredictedWordsMetric<IString, String>(references);
    } else if (evalMetric.equals("maxPredictedWords")) {
      emetric = new MaxPredictedWordsMetric<IString, String>(references);
    } else if (evalMetric.equals("bleuAfterPrefix")) {
        emetric = new BLEUAfterPrefixMetric<String>(references);
    } else {
			throw new UnsupportedOperationException(String.format( "Unrecognized metric: %s%n", evalMetric));

			// String[] s = matchLinearCombMetricPattern(evalMetric);
			// if(s != null) {
			// 	return makeLinearCombMetric(s, references);
			// }
			// else {
			// 	throw new UnsupportedOperationException(String.format(
			// 				 "Unrecognized metric: %s%n", evalMetric));
			// }
    }
    return emetric;
  }

	// public static String[] matchLinearCombMetricPattern(String scoreMetricStr) {
	// 	// Attempt pattern match
	// 	String weight = "[0-9\\.\\/]+";
	// 	String metric = "ter|bleu";
	// 	String weightMetricPair = String.format("(%s),(%s)", weight, metric);
		
	// 	String twoMetrics = String.format("\\(%s;%s\\)", weightMetricPair, weightMetricPair);
	// 	String threeMetrics = String.format("\\(%s;%s;%s\\)", weightMetricPair, weightMetricPair, weightMetricPair);
	// 	Pattern p2 = Pattern.compile(twoMetrics);
	// 	Pattern p3 = Pattern.compile(threeMetrics);
		
	// 	Matcher m2 = p2.matcher(scoreMetricStr);
	// 	Matcher m3 = p3.matcher(scoreMetricStr);
		
	// 	if(m2.matches()) {
	// 		// Groups: (1,2,3,4) => (weight1, metric1, weight2, metric2)
	// 		return new String[] {m2.group(1), m2.group(2), m2.group(3), m2.group(4)};
	// 	}
	// 	else if(m3.matches()) {
	// 		// Groups: (1,2,3,4,5,6) => (weight1, metric1, weight2, metric2, w3,m3)
	// 		return new String[] {m3.group(1), m3.group(2), 
	// 												 m3.group(3), m3.group(4),
	// 												 m3.group(5), m3.group(6)};
	// 	}
	// 	else {
	// 		return null;
	// 	}
	// }
	
	// public static LinearCombinationMetric makeLinearCombMetric(String[] args, 
	// 																										List<List<Sequence<IString>>> references)
	// 	{
	// 		EvaluationMetric<IString,String>[] metrics = new EvaluationMetric<IString,String>[args.length/2];
	// 		double[] weights = new double[args.length/2];
			
	// 		for(int i=0; i<args.length; i+=2) {			
	// 			String wStr = args[i];
	// 			String mStr = args[i+1];
				
	// 			Pattern frac = Pattern.compile("(\\d+)/(\\d+)");
	// 			Matcher mfrac = frac.matcher(wStr);
	// 			if(mfrac.matches()) {
	// 				weights[i/2]= Double.parseDouble(mfrac.group(1))/Double.parseDouble(mfrac.group(2));
	// 			}
	// 			else {
	// 				weights[i/2]=Double.parseDouble(wStr);
	// 			}
				
	// 			if(mStr.equals("bleu")) {
	// 				metrics[i/2] = new BLEUMetric<IString, String>(references);
	// 			}
	// 			else if(mStr.equals("ter")) {
	// 				metrics[i/2] = new TERpMetric<IString, String>(references);
	// 			}
	// 			else {
	// 				throw new UnsupportedOperationException("Unsupported loss function: " + mStr);
	// 			}
	// 		}
			
	// 		return new LinearCombinationMetric<IString,String>(weights, metrics);
	// 	}
	
}
