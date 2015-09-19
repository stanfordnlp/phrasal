package edu.stanford.nlp.mt.stats;

import java.util.HashSet;
import java.util.Set;

/**
 * Various similarity measures.
 * 
 * @author Spence Green
 *
 */
public final class SimilarityMeasures {

  private SimilarityMeasures() {};

  /**
   * Bigram Jacard score between two strings.
   * 
   * @param strA
   * @param strB
   * @return
   */
  public static double jaccard(String strA, String strB) {
    Set<String> setA = jaccardSet(strA);
    Set<String> setB = jaccardSet(strB);
    return jaccard(setA, setB);
  }

  /**
   * Return the Jacard distance between two strings.
   * 
   * @param strA
   * @param strB
   * @return
   */
  public static double jaccardDistance(String strA, String strB) {
    return 1.0 - jaccard(strA, strB);
  }

  /**
   * Convert a string to a set.
   * 
   * @param strA
   * @return
   */
  private static Set<String> jaccardSet(String strA) {
    Set<String> set = new HashSet<>();
    // Extract bigrams
    for (int i = 0, sz = strA.length(); i < sz; i += 2) {
      int end = Math.min(i+2, sz);
      String s = strA.substring(i, end);
      set.add(s);
    }
    return set;
  }

  /**
   * Abstract computation of the Jaccard score.
   * 
   * @param setA
   * @param setB
   * @return
   */
  public static <T> double jaccard(Set<T> setA, Set<T> setB) {
    if (setA.size() == 0 && setB.size() == 0) return 1.0;
    int setASize = setA.size();
    int setBSize = setB.size();
    setA.retainAll(setB);
    return setA.size() / (double) (setASize + setBSize - setA.size());
  }
}
