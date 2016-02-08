package edu.stanford.nlp.mt.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Computes the n-gram overlap between two files. The overlap is computed with respect
 * to file1. This is useful for measuring overlap between test and training sets.
 * 
 * NOTE: This implementation naively stores ngrams in sets, so it may not be appropriate
 * for large corpora and/or higher order ngrams.
 * 
 * @author Spence Green
 *
 */
public class NgramOverlap {

  /**
   * 
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.printf("Usage: java %s order file1 file2%n", NgramOverlap.class.getName());
      System.exit(-1);
    }

    final int maxOrder = Integer.parseInt(args[0]);
    final String file1 = args[1];
    final String file2 = args[2];

    final boolean doNISTTokenization = true;
    List<Sequence<IString>> file1Seqs = IStrings.tokenizeFile(file1, doNISTTokenization);
    List<Sequence<IString>> file2Seqs = IStrings.tokenizeFile(file2, doNISTTokenization);

    Map<Integer,Set<Sequence<IString>>> file1Ngrams = getNgrams(file1Seqs, maxOrder);
    Map<Integer,Set<Sequence<IString>>> file2Ngrams = getNgrams(file2Seqs, maxOrder);

    // Ngram overlap
    for (int order = 1; order <= maxOrder; ++order) {
      Set<Sequence<IString>> f1Order = file1Ngrams.get(order);
      int f1Sz = f1Order.size();
      Set<Sequence<IString>> f2Order = file2Ngrams.get(order);
      f1Order.retainAll(f2Order);
      System.out.printf("%d:\t%d/%d\t%.1f%%%n", order, f1Order.size(), f1Sz, (100.0 * f1Order.size()) / f1Sz);
    }

    // Exact match
    int numMatches = 0;
    for (Sequence<IString> x : file1Seqs) {
      for (Sequence<IString> y : file2Seqs) {
        if (x.equals(y)) ++numMatches;
      }
    }
    System.out.printf("Exact:\t%d/%d\t%.1f%n", numMatches, file1Seqs.size(), (100.0 * numMatches) /file1Seqs.size());
  }

  private static Map<Integer,Set<Sequence<IString>>> getNgrams(List<Sequence<IString>> seqs, int maxOrder) {
    Map<Integer,Set<Sequence<IString>>> ngrams = seqs.stream()
        .map(seq -> MetricUtils.<IString>getNGramCounts(seq, maxOrder).keySet()).flatMap(Set::stream)
        .collect(Collectors.groupingBy(Sequence<IString>::size, Collectors.toSet()));
    return ngrams;
  }
}
