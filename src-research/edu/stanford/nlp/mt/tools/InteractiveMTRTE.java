package edu.stanford.nlp.mt.tools;

import java.io.InputStreamReader;
import java.io.LineNumberReader;

import edu.stanford.nlp.rte.mtmetric.RTEFeaturizer;

/**
 * Interactive command line interface to MTRTE
 * 
 * @author daniel cer
 *
 */
public class InteractiveMTRTE {
   static public void main(String[] args) throws Exception {
     RTEFeaturizer featurizer = RTEFeaturizer.initialize(args);
      
     LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
     String ref = null;
     String mt = null;
      
     for (String line = reader.readLine(); line != null; line = reader.readLine()) {
       int id = reader.getLineNumber()-1;
       if (id % 2 == 0) {
         System.out.print("ref: ");
         ref = line;
       } else {            
         System.out.print("mt: ");
         mt = line;
       }
       double score = featurizer.mtScore(new String[]{ref}, mt);
       System.out.printf("MT RTE Score is: %e\n", score);
     }
   }
}
