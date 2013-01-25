package edu.stanford.nlp.mt.metrics;

import java.lang.reflect.Constructor;

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

  public static EvaluationMetric<IString, String> metric(String name,
      String referencePrefix) throws IOException {
    File f = new File(referencePrefix);
    if (f.exists()) {
      return metric(name,
          Metrics.readReferences(new String[] { referencePrefix }));
    }
    f = new File(referencePrefix + "0");
    if (f.exists()) {
      List<String> references = new ArrayList<String>();
      for (int i = 0;; i++) {
        f = new File(referencePrefix + i);
        if (!f.exists())
          break;
        references.add(referencePrefix + i);
      }
      return metric(name, Metrics.readReferences(references
          .toArray(new String[references.size()])));
    }

    throw new RuntimeException(String.format(
        "Invalid reference prefix: %s (file/files not found)\n",
        referencePrefix));
  }

  public static String THREAD_SAFE_TER = 
      "edu.stanford.nlp.mt.metrics.ThreadSafeTER";
  public static EvaluationMetric<IString, String> metric(String name,
      List<List<Sequence<IString>>> references) {
    if (name.equals(TER_STRING)) {
      EvaluationMetric<IString, String> emetric  = null;
      try {
        Class<EvaluationMetric> classEMetric = (Class<EvaluationMetric>)
          Class.forName(THREAD_SAFE_TER);
        Constructor<EvaluationMetric> constructor = 
          classEMetric.getConstructor(List.class);
        emetric = (EvaluationMetric<IString, String>) 
          constructor.newInstance(references);
        System.err.println("Using thread safe TER");
      } catch (ClassNotFoundException e) {
        System.err.printf("Warning: Can't find %s\n", THREAD_SAFE_TER);
      } catch (NoSuchMethodException e) {
        System.err.printf("Warning: Can't find standard constructor for %s\n", 
          THREAD_SAFE_TER);
      } catch (Exception e) {
        System.err.printf("Warning: Problem accessing standard constructor " +
          "for %s\n", THREAD_SAFE_TER);
      }

      if (emetric == null) {
        System.err.println("Warning: Using non-thread safe version of TER");
        System.err.println("distributed by Matthew Snoover");
        emetric = new TERMetric<IString, String>(references);
      }
      return emetric;
    } else if (name.equals(BLEU_STRING)) {
      return new BLEUMetric<IString, String>(references);
    } else if (name.equals(SMOOTH_BLEU_STRING)) {
      return new BLEUMetric<IString, String>(references, true);
    } else if (name.equals(NIST_STRING)) {
      return new NISTMetric<IString, String>(references);
    }
    throw new RuntimeException(String.format("Unrecognized metric: %s\n", name));
  }

  public static EvaluationMetric<IString, String> metric(
      List<List<Sequence<IString>>> references) {
    return new BLEUMetric<IString, String>(references);
  }
}
