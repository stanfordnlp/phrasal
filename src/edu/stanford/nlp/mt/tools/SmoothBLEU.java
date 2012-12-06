package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.metrics.NISTTokenizer;

/**
 * Compute smooth BLEU according to Lin and Och (2004).
 * 
 * @author daniel cer
 * @author Spence Green
 *
 */
public class SmoothBLEU {
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .printf("Usage: java %s [-order #] (ref 1) (ref 2) ... (ref n) < candidateTranslations%n", SmoothBLEU.class.getName());
      System.exit(-1);
    }

    int BLEUOrder = 4;
    if (args[0].equals("-order")) {
      BLEUOrder = Integer.parseInt(args[1]);
      String[] newArgs = new String[args.length - 2];
      System.arraycopy(args, 2, newArgs, 0, args.length - 2);
      args = newArgs;
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args);

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    int translationId = 0;
    for (String line; (line = reader.readLine()) != null; ) {
      line = NISTTokenizer.tokenize(line).trim();
      Sequence<IString> translation = new RawSequence<IString>(
          IStrings.toIStringArray(line.split("\\s+")));
      double score = 
          BLEUMetric.computeLocalSmoothScore(translation, referencesList.get(translationId), BLEUOrder);
      System.out.printf("%d\t%.4f%n", translationId, score);
      ++translationId;
    }
  }
}
