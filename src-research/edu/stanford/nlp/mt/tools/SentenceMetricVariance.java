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
    if (args.length < 3) {
      System.err.printf("Usage: java %s name source ref [ref] < translations%n", SentenceMetricVariance.class.getName());
      System.exit(-1);
    }
    
    String name = args[0];
    String srcFile = args[1];
    String[] refFileNames = new String[args.length-2];
    System.arraycopy(args, 2, refFileNames, 0, refFileNames.length);
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(refFileNames);
    List<Sequence<IString>> sourceList = IStrings.tokenizeFile(srcFile);
    
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));
//    LineNumberReader reader = IOTools.getReaderFromFile("mt05.mt06.dense-eb-01.trans");

    // Output files
    final int numReferences = refFileNames.length;
    PrintStream shortFile = IOTools.getWriterFromFile(name + ".short.tsv");
    shortFile.print("srcid\tsrclen\ttgtlen");
    for (int i = 0; i < numReferences; ++i) {
      shortFile.printf("\tref%d\treflen%d", i, i);
    }
    shortFile.printf("\trefAll\trefAllLen%n");

    PrintStream longFile = IOTools.getWriterFromFile(name + ".long.tsv");
    longFile.println("srcid\tsrclen\ttgtlen\trefid\treflen\tscore");
    for (String line; (line = reader.readLine()) != null; ) {
      final int sourceId = reader.getLineNumber() - 1;
      Sequence<IString> translation = IStrings.tokenize(line);
      final int tgtLen = translation.size();
      final int srcLen = sourceList.get(sourceId).size();
      List<Sequence<IString>> references = referencesList.get(sourceId);
      shortFile.printf("%d\t%d\t%d", sourceId, srcLen, tgtLen);
      double[] scoreArray = new double[numReferences];
      for (int i = 0; i < numReferences; ++i) {
        List<Sequence<IString>> ref = references.subList(i, i+1);
        assert ref.size() == 1;
        final int refLength = ref.get(0).size();
        scoreArray[i] = 
            BLEUMetric.computeLocalSmoothScore(translation, ref, ORDER, NAKOV_EXTENSION);
        shortFile.printf("\t%.4f\t%d", scoreArray[i], refLength);
        longFile.printf("%d\t%d\t%d\t%d\t%d\t%.4f%n", sourceId, srcLen, tgtLen, i, refLength, scoreArray[i]);
      }
      final int allLength = bestMatchLength(references, tgtLen);
      double scoreAllRefs = 
          BLEUMetric.computeLocalSmoothScore(translation, references, ORDER, NAKOV_EXTENSION);
      shortFile.printf("\t%.4f\t%d%n", scoreAllRefs, allLength);
      longFile.printf("%d\t%d\t%d\tall\t%d\t%.4f%n", sourceId, srcLen, tgtLen, allLength, scoreAllRefs);
    }
    shortFile.close();
    longFile.close();
  }
  
  // Copied from BLEUMetric.java
  private static int bestMatchLength(List<Sequence<IString>> references, int candidateLength) {
    int best = -1;
    for (Sequence<IString> reference : references) {
      int refLength = reference.size();
      if (best < 0 || Math.abs(candidateLength - best) > Math.abs(candidateLength
          - refLength)) {
        best = refLength;
      }
    }
    return best;
  }
}
