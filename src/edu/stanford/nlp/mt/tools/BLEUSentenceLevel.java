package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.metrics.NISTTokenizer;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Computes uncased, NIST-tokenized, BLEU+1 according to Lin and Och (2004).
 * 
 * @author daniel cer
 * @author Spence Green
 *
 */
public class BLEUSentenceLevel {
  
  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.printf("Usage: java %s [-order #] ref [ref] < candidateTranslations%n", 
              BLEUSentenceLevel.class.getName());
      System.exit(-1);
    }

    int BLEUOrder = BLEUMetric.DEFAULT_MAX_NGRAM_ORDER;
    if (args[0].equals("-order")) {
      BLEUOrder = Integer.parseInt(args[1]);
      String[] newArgs = new String[args.length - 2];
      System.arraycopy(args, 2, newArgs, 0, args.length - 2);
      args = newArgs;
    }
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args, true);

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    int sourceInputId = 0;
    for (String line; (line = reader.readLine()) != null; ) {
      line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      double score = 
          BLEUMetric.computeLocalSmoothScore(translation, referencesList.get(sourceInputId), BLEUOrder, false);
      System.out.printf("%.4f%n", score);
      ++sourceInputId;
    }
  }
}
