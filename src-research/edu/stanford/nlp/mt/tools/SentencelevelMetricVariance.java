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
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

/**
 * Computes the variance in BLEU+1 scores over various numbers
 * of references.
 * 
 * @author Spence Green
 *
 */
public class SentencelevelMetricVariance {

  private static final boolean NAKOV_EXTENSION = true;
  private static final int ORDER = 4;
  
  public static void main(String[] args) throws IOException {
    if (args.length < 3) {
      System.err.printf("Usage: java %s name source ref [ref] < translations%n", SentencelevelMetricVariance.class.getName());
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
      shortFile.printf("\tref%d\treflen%d\tref%dprec\tref%dBP", i, i, i, i);
    }
    shortFile.printf("\trefAll\trefAllLen\trefAllprec\trefAllBP%n");

    PrintStream longFile = IOTools.getWriterFromFile(name + ".long.tsv");
    longFile.println("srcid\tsrclen\ttgtlen\trefid\treflen\tscore\tprec\tBP");
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
        Pair<Double,Double> scoreComponents = computeLocalSmoothScore(translation, ref, ORDER, NAKOV_EXTENSION);
        scoreArray[i] = Math.exp(scoreComponents.first() + scoreComponents.second());
        shortFile.printf("\t%.4f\t%d\t%.4f\t%.4f", scoreArray[i], refLength, Math.exp(scoreComponents.first()), Math.exp(scoreComponents.second()));
        longFile.printf("%d\t%d\t%d\t%d\t%d\t%.4f\t%.4f\t%.4f%n", sourceId, srcLen, tgtLen, i, refLength, scoreArray[i], Math.exp(scoreComponents.first()), Math.exp(scoreComponents.second()));
      }
      final int allLength = bestMatchLength(references, tgtLen);
      Pair<Double,Double> scoreComponents = computeLocalSmoothScore(translation, references, ORDER, NAKOV_EXTENSION);
      double scoreAllRefs = Math.exp(scoreComponents.first() + scoreComponents.second());
      shortFile.printf("\t%.4f\t%d\t%.4f\t%.4f%n", scoreAllRefs, allLength, Math.exp(scoreComponents.first()), Math.exp(scoreComponents.second()));
      longFile.printf("%d\t%d\t%d\tall\t%d\t%.4f\t%.4f\t%.4f%n", sourceId, srcLen, tgtLen, allLength, scoreAllRefs, Math.exp(scoreComponents.first()), Math.exp(scoreComponents.second()));
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
  
  private static int bestMatchLength(int[] refLengths, int candidateLength) {
    int best = refLengths[0];
    for (int i = 1; i < refLengths.length; i++) {
      if (Math.abs(candidateLength - best) > Math.abs(candidateLength
          - refLengths[i])) {
        best = refLengths[i];
      }
    }
    return best;
  }
  
  // Copied from BLEUMetric.java
  private static <TK> Pair<Double,Double> computeLocalSmoothScore(Sequence<TK> seq,
      List<Sequence<TK>> refs, int order, boolean doNakovExtension) {

    Counter<Sequence<TK>> candidateCounts = Metrics.getNGramCounts(seq,
        order);
    Counter<Sequence<TK>> maxReferenceCount = Metrics.getMaxNGramCounts(refs, order);

    Metrics.clipCounts(candidateCounts, maxReferenceCount);
    int seqSz = seq.size();
    int[] localPossibleMatchCounts = new int[order];
    for (int i = 0; i < order; i++) {
      localPossibleMatchCounts[i] = possibleMatchCounts(i, seqSz);
    }

    double[] localCounts = localMatchCounts(candidateCounts,order);
    int localC = seq.size();
    int[] refLengths = new int[refs.size()];
    for (int i = 0; i < refLengths.length; i++) {
      refLengths[i] = refs.get(i).size();
    }
    int localR = bestMatchLength(refLengths, seq.size());
    if (doNakovExtension) ++localR;

    double localLogBP;
    if (localC < localR) {
      localLogBP = 1 - localR / (1.0 * localC);
    } else {
      localLogBP = 0.0;
    }

    double[] localPrecisions = new double[order];
    for (int i = 0; i < order; i++) {
      if (i == 0 && !doNakovExtension) {
        localPrecisions[i] = (1.0 * localCounts[i])
            / localPossibleMatchCounts[i];
      } else {
        localPrecisions[i] = (localCounts[i] + 1.0)
            / (localPossibleMatchCounts[i] + 1.0);
      }
    }
    double localNgramPrecisionScore = 0;
    for (int i = 0; i < order; i++) {
      localNgramPrecisionScore += (1.0 / order)
          * Math.log(localPrecisions[i]);
    }

    final double localScore = Math.exp(localLogBP + localNgramPrecisionScore);
    return new Pair<Double,Double>(localNgramPrecisionScore, localLogBP);
  }

  private static int possibleMatchCounts(int order, int length) {
    int d = length - order;
    return d >= 0 ? d : 0;
  }

  private static <TK> double[] localMatchCounts(Counter<Sequence<TK>> clippedCounts, int order) {
    double[] counts = new double[order];
    for (Sequence<TK> ngram : clippedCounts.keySet()) {
      double cnt = clippedCounts.getCount(ngram);
      if (cnt > 0.0) {
        int len = ngram.size();
        counts[len - 1] += cnt;
      }
    }

    return counts;
  }
}
