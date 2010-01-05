package edu.stanford.nlp.mt.metrics;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 */
public class MetricFactory {
	public static final String TER_STRING = "ter";
	public static final String BLEU_STRING = "bleu";
  public static final String NIST_STRING = "nist";
	public static final String SMOOTH_BLEU_STRING = "smoothbleu";
	
	public static EvaluationMetric<IString,String> metric(String name, String referencePrefix) throws IOException {
		File f = new File(referencePrefix);
		if (f.exists()) {
			return metric(name, Metrics.readReferences(new String[]{referencePrefix}));
		}
		f = new File(referencePrefix+"0");
		if (f.exists()) {
			List<String> references = new ArrayList<String>();
			for (int i = 0; ; i++) {
				f = new File(referencePrefix+i);
				if (!f.exists()) break;
				references.add(referencePrefix+i);
			}
			return metric(name, Metrics.readReferences(references.toArray(new String[0])));
		} 
		
		throw new RuntimeException(String.format("Invalid reference prefix: %s (file/files not found)\n", referencePrefix));
	}
	
	public static EvaluationMetric<IString,String> metric(String name, List<List<Sequence<IString>>> references) {
		if (name.equals(TER_STRING)) {
			return new TERMetric<IString, String>(references);
		} else if (name.equals(BLEU_STRING)) {
			return new BLEUMetric<IString, String>(references);
		} else if (name.equals(SMOOTH_BLEU_STRING)) {
			return new BLEUMetric<IString, String>(references, true);
    } else if (name.equals(NIST_STRING)) {
      return new NISTMetric<IString, String>(references);
    }
		throw new RuntimeException(String.format("Unrecognized metric: %s\n", name));
	}
	
	public static EvaluationMetric<IString,String> metric(List<List<Sequence<IString>>> references) {
		return new BLEUMetric<IString, String>(references);
	}
}
