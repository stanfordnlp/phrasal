package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.stats.Counters;

/**
 * Print a Phrasal weight vector to the console.
 * 
 * @author Spence Green
 *
 */
public class PrintWeights {
  
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.printf("Usage: java %s wts_file%n", PrintWeights.class.getName());
      System.exit(-1);
    }
    Counters.printCounterSortedByKeys(IOTools.readWeights(args[0]));
  }
}
