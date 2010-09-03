package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashSet;
import java.util.Set;


import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

public class CompareWeights {
   // TODO find a good permanent home for this method
   @SuppressWarnings("unchecked")
   static public Counter<String> readWeights(String filename) throws ClassNotFoundException, IOException {
      Counter<String> wts;
      
      if (filename.endsWith(".binwts")) {
         ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
               filename));
         wts = (Counter<String>) ois.readObject();
         ois.close();         
      } else {
         wts = new ClassicCounter<String>();
         BufferedReader reader = new BufferedReader(new FileReader(filename));
         for (String line; (line = reader.readLine()) != null;) {
            String[] fields = line.split("\\s+");
            wts.setCount(fields[0], Double.valueOf(fields[1]));
         }
         reader.close();
      }
      return wts;
   }
   
   static public void main(String[] args) throws Exception {
      if (args.length != 2) {
         System.err.println("Usage:\n\tjava ...CompareWeights wts1 wts2\n\nReturns: maximum absolute difference in a single weight value");
         System.exit(-1);
      }
      
      Set<String> allWeights = new HashSet<String>();
      Counter<String> wts1 = readWeights(args[0]);
      Counter<String> wts2 = readWeights(args[1]);
      allWeights.addAll(wts1.keySet());
      allWeights.addAll(wts2.keySet());
      
      double maxDiff = Double.NEGATIVE_INFINITY;
      for (String wt : allWeights) {
         double absDiff = Math.abs(wts1.getCount(wt) - wts2.getCount(wt));
         if (absDiff > maxDiff) maxDiff = absDiff;
      }
      System.out.println(maxDiff);
   }
}
