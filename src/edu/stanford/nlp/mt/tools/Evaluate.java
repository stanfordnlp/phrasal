package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.EvaluationMetricFactory;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * 
 * @author daniel cer
 *
 */
public class Evaluate {
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava edu.stanford.nlp.mt.tools.Evaluate \\\n"+
                   "\t\t(metric) (ref 1) (ref 2) ... (ref n) < candidateTranslations");
      System.exit(-1);
    }
    boolean perLine = System.getProperty("perLine") != null;
    
    String evalMetric = args[0];
    String[] refFn = Arrays.copyOfRange(args, 1, args.length);
    List<List<Sequence<IString>>> references = Metrics.readReferences(refFn);
    
    if (!perLine) {
      EvaluationMetric<IString,String> metric = EvaluationMetricFactory.newMetric(evalMetric, references);
      IncrementalEvaluationMetric<IString,String> incMetric = metric.getIncrementalMetric();
  
      LineNumberReader reader = new LineNumberReader(new InputStreamReader(
          System.in));
  
      for (String line; (line = reader.readLine()) != null; ) {
        Sequence<IString> translation = IStrings.tokenize(line);
        ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
            translation, null, 0);
        incMetric.add(tran);
      }
      reader.close();
      System.out.printf("%s = %.3f\n", evalMetric, 100 * incMetric.score());
      System.out.printf("Details:\n%s\n", incMetric.scoreDetails());
    } else {
  
      LineNumberReader reader = new LineNumberReader(new InputStreamReader(
          System.in));
  
      for (String line; (line = reader.readLine()) != null; ) {
        EvaluationMetric<IString,String> metric = EvaluationMetricFactory.newMetric(evalMetric, 
            Arrays.asList(references.get(reader.getLineNumber()-1)));        
        IncrementalEvaluationMetric<IString,String> incMetric = metric.getIncrementalMetric();
        Sequence<IString> translation = IStrings.tokenize(line);
        ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
            translation, null, 0);
        incMetric.add(tran);
        System.out.println(incMetric.score());
      }
      reader.close();  
    }    
  }
}
