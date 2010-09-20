package edu.stanford.nlp.mt.reranker;

import java.util.HashMap;

/**
 * An ngram: basically a glorified <String, Integer> pair. The most useful thing
 * here is the distribution() and maxDistribution() functions, which give
 * distributions of ngrams on sentences or sets of sentences.
 */
public class NGram {
  public String string;
  public int size;

  public NGram(String string, int size) {
    this.string = string.intern();
    this.size = size;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof NGram) && ((NGram) o).string == string
        && ((NGram) o).size == size;
  }

  @Override
  public int hashCode() {
    return string.hashCode() + Integer.valueOf(size).hashCode();
  }

  @Override
  public String toString() {
    return "\"" + string + "\" [" + size + "]";
  }

  public static HashMap<NGram, Integer> distribution(String[] sent) {
    return distribution(sent, 1, 4);
  }

  public static HashMap<NGram, Integer> maxDistribution(String[][] sents) {
    return maxDistribution(sents, 1, 4);
  }

  public static HashMap<NGram, Integer> maxDistribution(String[][] sents,
      int minSize, int maxSize) {
    HashMap<NGram, Integer> distrib = new HashMap<NGram, Integer>();
    for (int i = 0; i < sents.length; i++) {
      HashMap<NGram, Integer> sDistrib = distribution(sents[i], minSize,
          maxSize);
      for (NGram n : sDistrib.keySet())
        distrib.put(
            n,
            Math.max(distrib.containsKey(n) ? distrib.get(n) : 0,
                sDistrib.get(n)));
    }

    return distrib;
  }

  public static HashMap<NGram, Integer> distribution(String[] sent,
      int minSize, int maxSize) {
    HashMap<NGram, Integer> distrib = new HashMap<NGram, Integer>();

    for (int i = 0; i < sent.length; i++) {
      for (int j = minSize; j <= Math.min(maxSize, sent.length - i); j++) {
        String s = "";
        for (int k = i; k < i + j; k++)
          s += (s == "" ? "" : "-") + sent[k];
        NGram n = new NGram(s, j);
        distrib.put(n, (distrib.containsKey(n) ? distrib.get(n) : 0) + 1);
      }
    }

    return distrib;
  }

}
