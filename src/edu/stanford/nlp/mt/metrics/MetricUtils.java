package edu.stanford.nlp.mt.metrics;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.tools.NISTTokenizer;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Convenience methods for loading and collecting statistics from reference
 * translations.
 * 
 * @author danielcer
 * 
 */
public final class MetricUtils {
  
  private MetricUtils() {}

  private static final double LOG2 = Math.log(2);

  /**
   * 
   * @param <TK>
   */
  static public <TK> Counter<Sequence<TK>> getNGramCounts(Sequence<TK> sequence, int maxOrder) {
    Counter<Sequence<TK>> counts = new ClassicCounter<>();
    int sz = sequence.size();
    for (int i = 0; i < sz; i++) {
      int jMax = Math.min(sz, i + maxOrder);
      for (int j = i + 1; j <= jMax; j++) {
        Sequence<TK> ngram = sequence.subsequence(i, j);
        counts.incrementCount(ngram);
      }
    }
    return counts;
  }

  static public <TK> Counter<Sequence<TK>> getMaxNGramCounts(
      List<Sequence<TK>> sequences, int maxOrder) {
    return getMaxNGramCounts(sequences, null, maxOrder);
  }
  
  /**
   * Compute maximum n-gram counts from one or more sequences.
   * 
   * @param sequences - The list of sequences.
   * @param maxOrder - The n-gram order.
   */
  static public <TK> Counter<Sequence<TK>> getMaxNGramCounts(
      List<Sequence<TK>> sequences, double[] seqWeights, int maxOrder) {
    Counter<Sequence<TK>> maxCounts = new ClassicCounter<Sequence<TK>>();
    maxCounts.setDefaultReturnValue(0.0);
    if(seqWeights != null && seqWeights.length != sequences.size()) {
      throw new RuntimeException("Improper weight vector for sequences.");
    }
    
    int seqId = 0;
    for (Sequence<TK> sequence : sequences) {
      Counter<Sequence<TK>> counts = getNGramCounts(sequence, maxOrder);
      for (Sequence<TK> ngram : counts.keySet()) {
        double weight = seqWeights == null ? 1.0 : seqWeights[seqId];
        double countValue = weight * counts.getCount(ngram);
        double currentMax = maxCounts.getCount(ngram);
        maxCounts.setCount(ngram, Math.max(countValue, currentMax));
      }
      ++seqId;
    }
    return maxCounts;
  }

  /**
   * Calculates the "informativeness" of each ngram, which is used by the NIST
   * metric. In Matlab notation, the informativeness of the ngram w_1:n is
   * defined as -log2(count(w_1:n)/count(w_1:n-1)).
   * 
   * @param ngramCounts
   *          ngram counts according to references
   * @param totWords
   *          total number of words, which is used to compute the
   *          informativeness of unigrams.
   */
  static public <TK> Counter<Sequence<TK>> getNGramInfo(
      Counter<Sequence<TK>> ngramCounts, int totWords) {
    Counter<Sequence<TK>> ngramInfo = new ClassicCounter<Sequence<TK>>();

    for (Sequence<TK> ngram : ngramCounts.keySet()) {
      double num = ngramCounts.getCount(ngram);
      double denom = totWords;
      if (ngram.size() > 1) {
        Sequence<TK> ngramPrefix = ngram.subsequence(0,
            ngram.size() - 1);
        denom = ngramCounts.getCount(ngramPrefix);
      }
      double inf = -Math.log(num / denom) / LOG2;
      ngramInfo.setCount(ngram, inf);
      // System.err.printf("ngram info: %s %.3f\n", ngram.toString(), inf);
    }
    return ngramInfo;
  }

  public static <TK> void clipCounts(Counter<Sequence<TK>> counts,
      Counter<Sequence<TK>> maxRefCount) {
    Counters.minInPlace(counts, maxRefCount);
  }

  /*
   * static <TK> void clipCounts(Counter<Sequence<TK>> counts,
   * Counter<Sequence<TK>> maxRefCount) { for (Sequence<TK> ngram : new
   * HashSet<Sequence<TK>>(counts.keySet())) { Integer cnt =
   * maxRefCount.get(ngram); if (cnt == null) { counts.remove(ngram); continue;
   * } Integer altCnt = counts.get(ngram); if (cnt.compareTo(altCnt) < 0) {
   * counts.put(ngram, cnt); } //
   * System.err.printf("clipped count: %s Cnt: %d Orig: %d\n", ngram,
   * counts.get(ngram), altCnt); } }
   */

  static public List<List<Sequence<IString>>> readReferencesFromRoot(String root)
      throws IOException {
    int i = 0;
    List<String> files = new ArrayList<String>();
    for (;;) {
      String name = root + Integer.toString(i);
      if (!(new File(name).exists()))
        break;
      files.add(name);
      System.err.println("Found reference: " + name);
      ++i;
    }
    return readReferences(files.toArray(new String[files.size()]));
  }

  /**
   * Read a set of references from a list of newline-delimited files.
   * 
   * @param referenceFilenames
   * @return
   * @throws IOException
   */
  static public List<List<Sequence<IString>>> readReferences(
      String[] referenceFilenames) throws IOException {
    return readReferences(referenceFilenames, false);
  }

  /**
   * Read a set of referneces from a list of newline-delimited files. Optionally
   * apply NIST tokenization for evaluation.
   * 
   * @param referenceFilenames
   * @param applyNistTokenizer
   * @return
   * @throws IOException
   */
  public static List<List<Sequence<IString>>> readReferences(String[] referenceFilenames,
      boolean applyNistTokenizer) throws IOException {
    List<List<Sequence<IString>>> referencesList = new ArrayList<List<Sequence<IString>>>();
    for (String referenceFilename : referenceFilenames) {
      LineNumberReader reader = IOTools.getReaderFromFile(referenceFilename);
      for (String line; (line = reader.readLine()) != null;) {
        int lineNumber = reader.getLineNumber();
        if (referencesList.size() < lineNumber) {
          referencesList.add(new ArrayList<Sequence<IString>>(
              referenceFilenames.length));
        }
        if (applyNistTokenizer) {
          line = NISTTokenizer.tokenize(line);
        }
        referencesList.get(lineNumber - 1).add(IStrings.tokenize(line));
      }
      reader.close();
    }
    return referencesList;
  }
}
