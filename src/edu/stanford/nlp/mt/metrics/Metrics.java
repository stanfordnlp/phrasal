package edu.stanford.nlp.mt.metrics;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawIStringSequence;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Convenience methods for loading and collecting statistics from reference
 * translations.
 * 
 * @author danielcer
 * 
 */
public class Metrics {
  private Metrics() {
  }

  private static final double LOG2 = Math.log(2);

  /**
   * note: future plans for javanlp Count will make this method irrelevant.
   * 
   * @param <TK>
   */
  static private <TK> void IncCount(Map<Sequence<TK>, Integer> counts,
      Sequence<TK> sequence) {
    Integer cnt = counts.get(sequence);
    if (cnt == null) {
      counts.put(sequence, 1);
    } else {
      counts.put(sequence, cnt + 1);
    }
  }

  /**
   * 
   * @param <TK>
   */
  static public <TK> Map<Sequence<TK>, Integer> getNGramCounts(
      Sequence<TK> sequence, int maxOrder) {
    Map<Sequence<TK>, Integer> counts = new HashMap<Sequence<TK>, Integer>();

    int sz = sequence.size();
    for (int i = 0; i < sz; i++) {
      int jMax = Math.min(sz, i + maxOrder);
      for (int j = i + 1; j <= jMax; j++) {
        Sequence<TK> ngram = sequence.subsequence(i, j);
        IncCount(counts, ngram);
      }
    }
    return counts;
  }

  /**
   * Compute maximum n-gram counts from one or more sequences.
   * 
   * @param sequences - The list of sequences.
   * @param maxOrder - The n-gram order.
   * @return
   */
  static public <TK> Map<Sequence<TK>, Integer> getMaxNGramCounts(
      List<Sequence<TK>> sequences, int maxOrder) {
    Map<Sequence<TK>, Integer> maxCounts = new HashMap<Sequence<TK>, Integer>();

    for (Sequence<TK> sequence : sequences) {
      Map<Sequence<TK>, Integer> counts = getNGramCounts(sequence, maxOrder);
      for (Map.Entry<Sequence<TK>, Integer> sequenceIntegerEntry : counts
          .entrySet()) {
        Sequence<TK> ngram = sequenceIntegerEntry.getKey();
        int countValue = sequenceIntegerEntry.getValue();
        int currentMax = maxCounts.containsKey(ngram) ? maxCounts.get(ngram) : 0;
        maxCounts.put(ngram, Math.max(countValue, currentMax));
      }
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
  static public <TK> Map<Sequence<TK>, Double> getNGramInfo(
      Map<Sequence<TK>, Integer> ngramCounts, int totWords) {
    Map<Sequence<TK>, Double> ngramInfo = new HashMap<Sequence<TK>, Double>();

    for (Map.Entry<Sequence<TK>, Integer> sequenceIntegerEntry : ngramCounts
        .entrySet()) {
      double num = sequenceIntegerEntry.getValue();
      double denom = totWords;
      if (sequenceIntegerEntry.getKey().size() > 1) {
        Sequence<TK> ngramPrefix = sequenceIntegerEntry.getKey().subsequence(0,
            sequenceIntegerEntry.getKey().size() - 1);
        denom = ngramCounts.get(ngramPrefix);
      }
      double inf = -Math.log(num / denom) / LOG2;
      ngramInfo.put(sequenceIntegerEntry.getKey(), inf);
      // System.err.printf("ngram info: %s %.3f\n", ngram.toString(), inf);
    }
    return ngramInfo;
  }

  static <TK> void clipCounts(Map<Sequence<TK>, Integer> counts,
      Map<Sequence<TK>, Integer> maxRefCount) {
    for (Map.Entry<Sequence<TK>, Integer> e : counts.entrySet()) {
      Integer cnt = maxRefCount.get(e.getKey());
      if (cnt == null) {
        e.setValue(0);
        continue;
      }
      Integer altCnt = e.getValue();
      if (cnt.compareTo(altCnt) < 0) {
        e.setValue(cnt);
      }
      // System.err.printf("clipped count: %s Cnt: %d Orig: %d\n", ngram,
      // counts.get(ngram), altCnt);
    }
  }

  /*
   * static <TK> void clipCounts(Map<Sequence<TK>, Integer> counts,
   * Map<Sequence<TK>, Integer> maxRefCount) { for (Sequence<TK> ngram : new
   * HashSet<Sequence<TK>>(counts.keySet())) { Integer cnt =
   * maxRefCount.get(ngram); if (cnt == null) { counts.remove(ngram); continue;
   * } Integer altCnt = counts.get(ngram); if (cnt.compareTo(altCnt) < 0) {
   * counts.put(ngram, cnt); } //
   * System.err.printf("clipped count: %s Cnt: %d Orig: %d\n", ngram,
   * counts.get(ngram), altCnt); } }
   */

  static public List<List<Sequence<IString>>> readReferences(
      String[] referenceFilenames) throws IOException {
    return readReferences(referenceFilenames, true);
  }

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
    return readReferences(files.toArray(new String[files.size()]), true);
  }

  static public List<List<Sequence<IString>>> readReferences(
      String[] referenceFilenames, boolean NISTTokenize) throws IOException {
    List<List<Sequence<IString>>> referencesList = new ArrayList<List<Sequence<IString>>>();
    for (String referenceFilename : referenceFilenames) {
      LineNumberReader reader = new LineNumberReader(new FileReader(
          referenceFilename));
      for (String line; (line = reader.readLine()) != null;) {
        int lineNumber = reader.getLineNumber();
        if (referencesList.size() < lineNumber) {
          List<Sequence<IString>> list = new ArrayList<Sequence<IString>>(
              referenceFilenames.length);
          if (NISTTokenize)
            line = NISTTokenizer.tokenize(line).trim();
          list.add(new RawIStringSequence(IStrings.toIStringArray(line
              .split("\\s+"))));
          referencesList.add(list);
        } else {
          if (NISTTokenize) {
            referencesList.get(lineNumber - 1).add(
                new RawIStringSequence(IStrings.toIStringArray(NISTTokenizer
                    .tokenize(line).split("\\s+"))));
          } else {
            referencesList.get(lineNumber - 1).add(
                new RawIStringSequence(IStrings.toIStringArray(line
                    .split("\\s+"))));
          }
        }
      }
      reader.close();
    }
    return referencesList;
  }
}
