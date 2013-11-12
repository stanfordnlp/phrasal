package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.List;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.Metrics;

public class SentenceMetricVariance {

  private static final boolean NAKOV_EXTENSION = false;
  private static final int ORDER = 4;
  
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.printf("Usage: java %s name ref [ref] < translations%n", SentenceMetricVariance.class.getName());
      System.exit(-1);
    }
    
    String name = args[0];
    String[] refFileNames = new String[args.length-1];
    System.arraycopy(args, 1, refFileNames, 0, refFileNames.length);
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(refFileNames);

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    // Output files
    final int numReferences = refFileNames.length;
    PrintStream shortFile = IOTools.getWriterFromFile(name + ".short.tsv");
    shortFile.print("srcid");
    for (int i = 0; i < numReferences; ++i) {
      shortFile.printf("\tref%d", i);
    }
    shortFile.printf("\trefAll%n");

    PrintStream longFile = IOTools.getWriterFromFile(name + ".long.tsv");
    longFile.println("srcid\trefid\tscore");
    for (String line; (line = reader.readLine()) != null; ) {
      Sequence<IString> translation = IStrings.tokenize(line);
      int sourceId = reader.getLineNumber() - 1;
      List<Sequence<IString>> references = referencesList.get(sourceId);
      shortFile.print(sourceId);
      double[] scoreArray = new double[numReferences];
      for (int i = 0; i < numReferences; ++i) {
        scoreArray[i] = 
            BLEUMetric.computeLocalSmoothScore(translation, references.subList(i, i+1), ORDER, NAKOV_EXTENSION);
        shortFile.printf("\t%.4f", scoreArray[i]);
        longFile.printf("%d\t%d\t%.4f%n", sourceId, i, scoreArray[i]);
      }
      double scoreAllRefs = 
          BLEUMetric.computeLocalSmoothScore(translation, references, ORDER, NAKOV_EXTENSION);
      shortFile.printf("\t%.4f%n", scoreAllRefs);
      longFile.printf("%d\tall\t%.4f%n", sourceId, scoreAllRefs);
    }
    shortFile.close();
    longFile.close();
  }
}
