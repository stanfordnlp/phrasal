package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.Map.Entry;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.stats.Counter;


public class OverrideBinwts {
  private static void usage() {
    System.err.println("Usage: java " + OverrideBinwts.class.getName() + " input.binwts overrides.txt output.binwts");
  }
  
  public static void main(String[] args) {
    if(args.length != 3) {
      usage();
      System.exit(-1);
    }
      
    String input = args[0];
    String overrides = args[1];
    String output = args[2];
    
    System.err.println("reading weights from " + input);
    
    Counter<String> weights = IOTools.readWeights(input);
    
    try {
      Counter<String> overridesW = IOTools.readWeightsPlain(overrides);
      System.err.println("read weights from  " + overrides + ":");
      for(Entry<String,Double> entry : overridesW.entrySet()) {
        if(entry.getValue() == 0) weights.remove(entry.getKey());
        else weights.setCount(entry.getKey(), entry.getValue());
        System.err.println("setting feature: " + entry.getKey() + " = " + entry.getValue());
      }
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }

    System.err.println("writing weights to " + output);
    
    IOTools.writeWeights(output, weights);
    
  }
  
}
