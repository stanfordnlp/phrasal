package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

public class ShowWeights {
   static public void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.err.println("Usage:\n\tjava ...ShowWeights (wts)\n");
         System.exit(-1);
      }
      Counter<String> wts = CompareWeights.readWeights(args[0]);
      
      for (Pair<String,Double> p : Counters.toDescendingMagnitudeSortedListWithCounts(wts)) {
         System.out.printf("%s %g\n", p.first, p.second);
       }
   }
}
