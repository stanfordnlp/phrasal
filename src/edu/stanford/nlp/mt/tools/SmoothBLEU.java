package edu.stanford.nlp.mt.tools;

import java.util.List;

import edu.stanford.nlp.mt.metrics.BLEUMetric;

import java.util.Arrays;

/**
 * Interactive smooth BLEU utility
 * 
 * @author daniel cer
 *
 */
public class SmoothBLEU {
  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("Usage:\n\tjava ...SmoothBLEU \"this is ref1|||this is ref2\" \"translation\" (order)\n");
      System.exit(-1);
    }
      
    String[] refArr = args[0].split("\\|\\|\\|");
    
    List<String> refs = Arrays.asList(refArr);
    String trans = args[1];
    int order = Integer.parseInt(args[2]);
    double smoothBLEU = BLEUMetric.computeLocalSmoothScore(trans, refs, order);
    System.out.printf("%.3f\n", smoothBLEU*100);
  }
}
