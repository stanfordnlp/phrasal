package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetricFactory;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.Metrics;

/**
 * NbestEvaluatationAnnotation is a utility for annotating 
 * n-best lists with sentence level evaluation metric scores.
 * 
 * 
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class NbestEvaluatationAnnotation {

  static final boolean TOKENIZE_NIST = false;
  
  static public void usage() {
    err.printf("Usage:\n\t%s (input n-best) (metric) (output annotated n-best) (refs)\n", NbestEvaluatationAnnotation.class.getName());
  }
  
  static public void main(String[] args) throws Exception {
    if (args.length < 4) {
      usage();
      exit(-1);
    }
    
    String nbestFn = args[0];
    String metricName = args[1];
    String nbestOutFn = args[2];    
    String[] refFns = new String[args.length-3];
    for (int i = 0; i < refFns.length; i++) {
      refFns[i] = args[i+3];
    }
    
    FlatNBestList nbest = new FlatNBestList(nbestFn);
    
    List<List<Sequence<IString>>> refs= Metrics.readReferences(
        refFns, TOKENIZE_NIST);
    
    List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists = nbest.nbestLists();
    
    PrintWriter nbestOut = new PrintWriter(new FileWriter(nbestOutFn));
    
    for (int id = 0; id < nbestLists.size(); id++) {
      List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists.get(id);
      
      EvaluationMetric<IString,String> emetric = EvaluationMetricFactory.newMetric(metricName, refs.subList(id, id+1));
      for (ScoredFeaturizedTranslation<IString, String> tran : nbestList) {
        List<ScoredFeaturizedTranslation<IString,String>> trans = 
            new ArrayList<ScoredFeaturizedTranslation<IString,String>>(1);
        trans.add(tran);
        double emetricScore = emetric.score(trans);
        nbestOut.printf("%d ||| %s ||| %f\n", id, tran.toString(), emetricScore);
      }
    }
    nbestOut.close();
  }
}
