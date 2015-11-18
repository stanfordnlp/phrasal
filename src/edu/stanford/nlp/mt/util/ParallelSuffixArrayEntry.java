package edu.stanford.nlp.mt.util;

import java.util.stream.IntStream;

import edu.stanford.nlp.mt.train.GIZAWordAlignment;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;

/**
 * An entry in the suffix array.
 * 
 * @author Spence Green
 *
 */
public class ParallelSuffixArrayEntry {
  public final String[] source;
  public final String[] target;
  public final int[][] f2e;
  // If the query was a target, then this is the left edge in the target array.
  // Otherwise, it is the left edge in the source array.
  public final int queryLeftEdge;
  
  /**
   * Constructor.
   * 
   * @param s
   * @param v
   */
  public ParallelSuffixArrayEntry(SentencePair s, Vocabulary v) {
    this.queryLeftEdge = s.wordPosition;
    source = IntStream.range(0, s.sourceLength()).mapToObj(i -> v.get(s.source(i))).toArray(String[]::new);
    target = IntStream.range(0, s.targetLength()).mapToObj(i -> v.get(s.target(i))).toArray(String[]::new);
    f2e = new int[source.length][];
    for (int i = 0; i < source.length; ++i) {
      f2e[i] = s.f2e(i);
    }
  }
  
  @Override
  public String toString() {
    String nl = System.getProperty("line.separator");
    StringBuilder sb = new StringBuilder(2*source.length + target.length);
    sb.append(String.join(" ", source)).append(nl)
      .append(String.join(" ", target)).append(nl)
      .append(GIZAWordAlignment.toGizaString(f2e));
    return sb.toString();
  }
}
