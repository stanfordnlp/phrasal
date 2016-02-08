package edu.stanford.nlp.mt.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import edu.stanford.nlp.mt.util.IString;


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

    } else if (scoreMetricStr.equals("ter") || scoreMetricStr.equals("tergain")) {
      return "ter";
      
    } else if (scoreMetricStr.equals("numPredictedWords")) {
      return scoreMetricStr;
      
    } else if (scoreMetricStr.equals("nextPredictedWord")) {
      return scoreMetricStr;
      
    } else if (scoreMetricStr.equals("bleu-prefix")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleup-nextw/2")) {
      return scoreMetricStr;
      
    } else if (scoreMetricStr.equals("100bleup-nextw/2")) {
      return scoreMetricStr;
      
    } else if (scoreMetricStr.equals("2bleu-ter") || scoreMetricStr.equals("2bleun-ter")) {
      return scoreMetricStr;
    
    } else if (scoreMetricStr.equals("bleu-ter") || scoreMetricStr.equals("bleun-ter")) {
      return "bleu-ter";
    
    } else if (scoreMetricStr.equals("bleu-2ter") || scoreMetricStr.equals("bleun-2ter")) {
      return "bleu-2ter";
    
    } else if (scoreMetricStr.equals("bleun-ter/2") || scoreMetricStr.equals("bleu-ter/2")) {
      return "bleu-ter/2";
    
    } else if (scoreMetricStr.equals("bleu-ter-len/3")) {
      return "bleu-ter/2";
      
    } else if (scoreMetricStr.equals("bleunX2ter")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleunXter")) {
      throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
    
    } else if (scoreMetricStr.equals("bleun-2fastter")) {
      return "bleu-2ter";
      
    } else {
			String[] s = matchLinearCombMetricPattern(scoreMetricStr);
			if(s != null) {
				// TODO: Fix this
				return "bleu-ter/2";
				//return scoreMetricStr;
			}
			else {
				throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
			}
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
      return new BLEUGain<>();

    } else if (scoreMetricStr.equals("bleu-smooth-unscaled")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<>(DEFAULT_ORDER, false, false);
    
    } else if (scoreMetricStr.equals("bleu-nakov")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<>(true);
    
    } else if (scoreMetricStr.equals("bleu-nakov-unscaled")) {
      // Nakov's extensions to BLEU+1
      return new BLEUGain<>(DEFAULT_ORDER, true, false);
    
    } else if (scoreMetricStr.equals("bleu-chiang")) {
      // Chiang's oracle document and exponential decay
      return new BLEUOracleCost<>(DEFAULT_ORDER, false);

    } else if (scoreMetricStr.equals("bleu-cherry")) {
      // Cherry and Foster (2012)
      return new BLEUOracleCost<>(DEFAULT_ORDER, true);
    
    } else if (scoreMetricStr.equals("bleu-prefix")) {
      return new SLBLEUAfterPrefix<>(DEFAULT_ORDER, true);
      
    } else if (scoreMetricStr.equals("tergain")) {
      return new SLTERGain<>();
      
    } else if (scoreMetricStr.equals("ter")) {
      return new SLTERMetric<>();
      
    } else if (scoreMetricStr.equals("numPredictedWords")) {
      return new LocalNumPredictedWordsMetric<>();

    } else if (scoreMetricStr.equals("nextPredictedWord")) {
      return new LocalNextPredictedWordMetric<>();
      
    } else if (scoreMetricStr.equals("maxPredictedWords")) {
      return new MaxPredictedWordsMetric.Local<>();

    } else if (scoreMetricStr.equals("2bleun-ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{2.0, 1.0}, metrics);

    } else if (scoreMetricStr.equals("bleun-ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{1.0, 1.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleun-2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>());
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleun-ter/2")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{0.5, 0.5}, metrics);
    
    } else if (scoreMetricStr.equals("bleu-ter/2")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>());
      metrics.add(new SLTERMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{0.5, 0.5}, metrics);
      
    } else if (scoreMetricStr.equals("bleu-ter-len/3")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(3);
      metrics.add(new BLEUGain<>());
      metrics.add(new SLTERMetric<>());
      metrics.add(new LengthMetric<>());
      return new SLLinearCombinationMetric<>(
        new double[]{1.0/3.0, 1.0/3.0, 1.0/3.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleunX2ter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLGeometricCombinationMetric<>(
        new double[]{1.0, 2.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleunXter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>());
      return new SLGeometricCombinationMetric<>(
        new double[]{1.0, 1.0}, new boolean[]{false, true}, metrics);
    
    } else if (scoreMetricStr.equals("bleun-2fastter")) {
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new BLEUGain<>(true));
      metrics.add(new SLTERMetric<>(5));
      return new SLLinearCombinationMetric<>(new double[]{1.0, 2.0}, metrics);
    
    } else if (scoreMetricStr.equals("bleup-nextw/2")) {  
      List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
      metrics.add(new SLBLEUAfterPrefix<>(DEFAULT_ORDER, true));
      metrics.add(new LocalNextPredictedWordMetric<>());
      return new SLLinearCombinationMetric<>(new double[]{1.0, 1.0}, metrics);
      
      } else if (scoreMetricStr.equals("100bleup-nextw/2")) {  
        List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(2);
        metrics.add(new SLBLEUAfterPrefix<>(DEFAULT_ORDER, true));
        metrics.add(new LocalNextPredictedWordMetric<>());
        return new SLLinearCombinationMetric<>(new double[]{1.0, 0.01}, metrics);
              
    } else {
			String[] s = matchLinearCombMetricPattern(scoreMetricStr);
			if(s!=null) {
				return makeLinearCombMetric(s);
			}
			else {
				throw new UnsupportedOperationException("Unsupported loss function: " + scoreMetricStr);
			}
    }
  }

	public static String[] matchLinearCombMetricPattern(String scoreMetricStr) {
		// Attempt pattern match
		String weight = "[0-9\\.\\/]+";
		String metric = "ter|bleu|length";
		String weightMetricPair = String.format("(%s),(%s)", weight, metric);
		
		String twoMetrics = String.format("\\(%s;%s\\)", weightMetricPair, weightMetricPair);
		String threeMetrics = String.format("\\(%s;%s;%s\\)", weightMetricPair, weightMetricPair, weightMetricPair);
		Pattern p2 = Pattern.compile(twoMetrics);
		Pattern p3 = Pattern.compile(threeMetrics);
		
		Matcher m2 = p2.matcher(scoreMetricStr);
		Matcher m3 = p3.matcher(scoreMetricStr);
		
		if(m2.matches()) {
			// Groups: (1,2,3,4) => (weight1, metric1, weight2, metric2)
			return new String[] {m2.group(1), m2.group(2), m2.group(3), m2.group(4)};
		}
		else if(m3.matches()) {
			// Groups: (1,2,3,4,5,6) => (weight1, metric1, weight2, metric2, w3,m3)
			return new String[] {m3.group(1), m3.group(2), 
													 m3.group(3), m3.group(4),
													 m3.group(5), m3.group(6)};
		}
		else {
			return null;
		}
	}
	
	public static SLLinearCombinationMetric<IString, String> makeLinearCombMetric(String[] args) {
		List<SentenceLevelMetric<IString,String>> metrics = new ArrayList<>(args.length/2);
		double[] weights = new double[args.length/2];

		for(int i=0; i<args.length; i+=2) {			
			String wStr = args[i];
			String mStr = args[i+1];
			

			Pattern frac = Pattern.compile("(\\d+)/(\\d+)");
			Matcher mfrac = frac.matcher(wStr);
			if(mfrac.matches()) {
				weights[i/2]= Double.parseDouble(mfrac.group(1))/Double.parseDouble(mfrac.group(2));
			}
			else {
				weights[i/2]=Double.parseDouble(wStr);
			}


			if(mStr.equals("bleu")) {
				metrics.add(new BLEUGain<IString,String>());
			}
			else if(mStr.equals("ter")) {
				metrics.add(new SLTERMetric<IString,String>());
			}
			else if(mStr.equals("length")) {
				metrics.add(new LengthMetric<IString,String>());
			}
			else {
				throw new UnsupportedOperationException("Unsupported loss function: " + mStr);
			}
		}

		return new SLLinearCombinationMetric<IString,String>(weights, metrics);
	}
}
