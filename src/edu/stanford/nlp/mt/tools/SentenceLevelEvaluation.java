package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.metrics.TERpMetric;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Computes sentence-level metrics, printing one score per line.
 * 
 * @author Spence Green
 *
 */
public class SentenceLevelEvaluation {
  
  private static double getScore(Sequence<IString> translation,
      List<Sequence<IString>> list, int order, String metric) {
    if (metric.equals("bleu")) {
      return BLEUMetric.computeLocalSmoothScore(translation, list, order, false);
    
    } else if (metric.equals("ter")) {
      return TERpMetric.computeLocalTERScore(translation, list);
      
    } else {
      throw new RuntimeException("Unknown metric: " + metric);
    }
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(SentenceLevelEvaluation.class.getName()).append(" ref [ref] < candidateTranslations").append(nl);
    sb.append(nl);
    sb.append(" Options:").append(nl);
    sb.append("   -order num      : ngram order (default: 4)").append(nl);
    sb.append("   -no-nist        : Disable NIST tokenization").append(nl);
    sb.append("   -metric str     : Metric [ter, bleu] (default: bleu)").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<String,Integer>();
    argDefs.put("order", 1);
    argDefs.put("no-nist", 0);
    argDefs.put("metric", 1);
    return argDefs;
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.print(usage());
      System.exit(-1);
    }
    
    Properties options = StringUtils.argsToProperties(args, argDefs());
    int ngramOrder = PropertiesUtils.getInt(options, "order", BLEUMetric.DEFAULT_MAX_NGRAM_ORDER);
    boolean disableTokenization = PropertiesUtils.getBool(options, "no-nist", false);
    String metric = options.getProperty("metric", "bleu");

    String[] refs = options.getProperty("").split("\\s+");
    List<List<Sequence<IString>>> referencesList = MetricUtils.readReferences(refs, ! disableTokenization);
    System.err.printf("Metric: %s with %d references%n", metric, referencesList.get(0).size());
    
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));
    int sourceInputId = 0;
    for (String line; (line = reader.readLine()) != null; ++sourceInputId) {
      line = disableTokenization ? line : NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      double score = getScore(translation, referencesList.get(sourceInputId), ngramOrder, metric);
      System.out.printf("%.4f%n", score);
    }
    System.err.printf("Scored %d input segments%n", sourceInputId);
  }
}
