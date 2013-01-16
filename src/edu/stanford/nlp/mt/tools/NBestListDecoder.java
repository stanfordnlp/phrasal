package edu.stanford.nlp.mt.tools;

import static java.lang.System.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.StaticScorer;
import edu.stanford.nlp.mt.decoder.util.Scorer;

import edu.stanford.nlp.stats.Counter;
/**
 * NBestListDecoder is a utility for finding the highest scoring 
 * hypotheses on a set of n-best lists given a different weight 
 * vector than what was used to produce the n-best lists.
 *  
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class NBestListDecoder {
  static public void usage() {
    err.printf("Usage:\n\t%s \\\n"+
      "\t  (n-best list) (alternative weights) (output translations)\n", 
      NBestListDecoder.class.getName());
  }
  
  static public void main(String[] args) throws Exception {
    if (args.length != 3) {
      usage();
      exit(-1);
    }
    
    String nbestFn = args[0];
    String weightsFn = args[1];
    String outputFn = args[2];
    
    FlatNBestList nbest = new FlatNBestList(nbestFn);
    Counter<String> weights = IOTools.readWeights(weightsFn);
    Scorer<String> scorer = new StaticScorer(weights);
    
    BufferedWriter outputFh = new BufferedWriter(new FileWriter(outputFn));
    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestList : nbestLists) {
      double bestScore = Double.NEGATIVE_INFINITY;
      ScoredFeaturizedTranslation<IString,String> bestTrans = null;
       for (ScoredFeaturizedTranslation<IString, String> trans : nbestList) {
         double score = scorer.getIncrementalScore(trans.features);
         if (score > bestScore) {
           bestScore = score;
           bestTrans = trans;
         }
       }
       outputFh.write(bestTrans.translation.toString());
       outputFh.write("\n");
    }
    outputFh.close();
  }
}
