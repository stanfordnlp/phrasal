package edu.stanford.nlp.mt.tools;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;

import edu.stanford.nlp.stats.Counter;

/**
 * NBestReranker is a utility for finding the highest scoring 
 * hypotheses on a set of n-best lists given a different weight 
 * vector than what was used to produce the n-best lists.
 *  
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class NBestReranker {
  
  static public void main(String[] args) throws Exception {
    if (args.length != 3 && args.length != 4) {
      System.err.printf("Usage:\n\t%s \\%n"+
          "\t  (n-best list) (alternative weights) (output translations)%n"+
          "\t  (n-best list) (alternative weights) (output translations) (output indices of new 1-bests)%n", 
          NBestReranker.class.getName());
      System.exit(-1);
    }
    
    String nbestFn = args[0];
    String weightsFn = args[1];
    String outputFn = args[2];
		String outputNew1BestFn = null;


    BufferedWriter outputNew1BestFh = null;
		if(args.length >= 4) {
			outputNew1BestFn = args[3];
			outputNew1BestFh = new BufferedWriter(new FileWriter(outputNew1BestFn));
		}
    
    FlatNBestList nbest = new FlatNBestList(nbestFn);
    Counter<String> weights = IOTools.readWeights(weightsFn);
    Scorer<String> scorer = new DenseScorer(weights);
    final String nl = System.getProperty("line.separator");
    BufferedWriter outputFh = new BufferedWriter(new FileWriter(outputFn));
    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();

    int id = 0;
    for (List<ScoredFeaturizedTranslation<IString, String>> nbestList : nbestLists) {
      double bestScore = Double.NEGATIVE_INFINITY;
      ScoredFeaturizedTranslation<IString,String> bestTrans = null;
			int bestPos=0;
      
      // Thang Mar14: check for null nbestList
      if(nbestList==null){
        System.err.printf("null nbest list for sent %d\n", id);
        System.exit(1);
      }
			int currPos=0;
      for (ScoredFeaturizedTranslation<IString, String> trans : nbestList) {
        double score = scorer.getIncrementalScore(trans.features);
        if (score > bestScore) {
          bestScore = score;
          bestTrans = trans;
					bestPos   = currPos;
        }
				currPos++;
      }
      outputFh.write(bestTrans.translation.toString());
      outputFh.write(nl);
			if(outputNew1BestFh != null) {
				outputNew1BestFh.write(Integer.toString(bestPos));
				outputNew1BestFh.write(nl);
			}

      id++;
    }
    outputFh.close();
		if(outputNew1BestFh != null) {
			outputNew1BestFh.close();
		}
  }
}
