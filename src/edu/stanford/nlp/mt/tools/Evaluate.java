package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Run a corpus-level evaluation metric. See CorpusLevelMetricFactory for the
 * list of available metrics.
 * 
 * @author daniel cer
 * @author Spence Green
 * 
 */
public class Evaluate {

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(Evaluate.class.getName()).append(" metric-name ref [ref] < candidateTranslations").append(nl);
    sb.append(nl);
    sb.append(" Options:").append(nl);
    sb.append("   -no-nist        : Disable NIST tokenization").append(nl);
    sb.append("   -cased          : Cased evaluation").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<>();
    argDefs.put("no-nist", 0);
    argDefs.put("cased", 0);
    return argDefs;
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.print(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, argDefs());
    boolean disableTokenization = PropertiesUtils.getBool(options, "no-nist", false);
    boolean doCased = PropertiesUtils.getBool(options, "cased", false);

    // Setup the metric tokenization scheme. Applies to both the references and
    // hypotheses
    if (doCased) NISTTokenizer.lowercase(false);
    NISTTokenizer.normalize( ! disableTokenization);

    // Load the references
    String[] parsedArgs = options.getProperty("").split("\\s+");
    final String evalMetric = parsedArgs[0];
    String[] refs= Arrays.copyOfRange(parsedArgs, 1, parsedArgs.length);
    final List<List<Sequence<IString>>> references = MetricUtils.readReferences(refs, true);
    System.out.printf("Metric: %s with %d references%n", evalMetric, refs.length);

    EvaluationMetric<IString,String> metric = CorpusLevelMetricFactory.newMetric(evalMetric, references);
    IncrementalEvaluationMetric<IString,String> incMetric = metric.getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));
    for (String line; (line = reader.readLine()) != null; ) {
      line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<>(
          translation, null, 0);
      incMetric.add(tran);
    }
    // Check for an incomplete set of translations
    if (reader.getLineNumber() < references.size()) {
      System.err.printf("WARNING: Translation candidate file is shorter than references (%d/%d)%n", 
          reader.getLineNumber(), references.size());
    }
    reader.close();

    System.out.printf("%s = %.3f%n", evalMetric, 100 * Math.abs(incMetric.score()));
    System.out.printf("Details:%n%s%n", incMetric.scoreDetails());
  }
}
