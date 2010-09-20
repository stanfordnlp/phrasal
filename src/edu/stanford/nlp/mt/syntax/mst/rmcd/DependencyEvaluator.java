package edu.stanford.nlp.mt.syntax.mst.rmcd;

import edu.stanford.nlp.mt.syntax.mst.rmcd.io.*;

public class DependencyEvaluator {

  public static void evaluate(String act_file, String pred_file,
      String act_format, String pred_format) throws Exception {

    DependencyReader goldReader = DependencyReader.createDependencyReader(null,
        act_format, null);
    boolean labeled = goldReader.startReading(act_file, null, null);

    DependencyReader predictedReader = DependencyReader.createDependencyReader(
        null, pred_format, null);
    boolean predLabeled = predictedReader.startReading(pred_file, null, null);

    if (labeled != predLabeled)
      System.out
          .println("Gold file and predicted file appear to differ on whether or not they are labeled. Expect problems!!!");

    int total = 0;
    int corr = 0;
    int corrL = 0;
    int numsent = 0;
    int corrsent = 0;
    int corrsentL = 0;

    DependencyInstance goldInstance = goldReader.getNext();
    DependencyInstance predInstance = predictedReader.getNext();

    while (goldInstance != null) {

      int instanceLength = goldInstance.length();

      if (instanceLength != predInstance.length())
        throw new RuntimeException(String.format(
            "Lengths do not match on sentence %d: %d != %d\n", numsent,
            instanceLength, predInstance.length()));

      boolean whole = true;
      boolean wholeL = true;

      // NOTE: the first item is the root info added during nextInstance(), so
      // we skip it.

      for (int i = 1; i < instanceLength; i++) {
        if (predInstance.getHead(i) == goldInstance.getHead(i)) {
          corr++;
          if (labeled) {
            if (goldInstance.getDepRel(i).equals(predInstance.getDepRel(i)))
              corrL++;
            else
              wholeL = false;
          }
        } else {
          whole = false;
          wholeL = false;
        }
      }
      total += instanceLength - 1; // Subtract one to not score fake root token

      if (whole)
        corrsent++;
      if (wholeL)
        corrsentL++;
      numsent++;

      goldInstance = goldReader.getNext();
      predInstance = predictedReader.getNext();
    }

    System.out.println("Tokens: " + total);
    System.out.println("Correct: " + corr);
    System.out.println("Unlabeled Accuracy: " + ((double) corr / total));
    System.out.println("Unlabeled Complete Correct: "
        + ((double) corrsent / numsent));
    if (labeled) {
      System.out.println("Labeled Accuracy: " + ((double) corrL / total));
      System.out.println("Labeled Complete Correct: "
          + ((double) corrsentL / numsent));
    }

  }

  public static void main(String[] args) throws Exception {
    String format = "CONLL";
    if (args.length > 2)
      format = args[2];

    evaluate(args[0], args[1], format, format);
  }

}
