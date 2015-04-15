package edu.stanford.nlp.mt.train;

import com.google.common.collect.ConcurrentHashMultiset;

/**
 * Extractor for printing the number of occurrences of each alignment template.
 * 
 * @author Michel Galley
 */
public class CountFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugCountFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));

  private static final double EXP_M1 = Math.exp(-1);

  private ConcurrentHashMultiset<Integer> feCounts = ConcurrentHashMultiset.create();

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
    if (getCurrentPass() + 1 == getRequiredPassNumber())
      addCountToArray(feCounts, alTemp.getKey());
  }

  @Override
  public Object score(AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    double c = feCounts.count(idx);
    return new double[] { c, ((c > 1) ? 1.0 : EXP_M1) };
  }

  private static void addCountToArray(final ConcurrentHashMultiset<Integer> counter, int idx) {
    if (idx < 0)
      return;
    counter.add(idx);
    if (DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx=" + idx + " in vector (" + counter
          + ").");
  }

  @Override
  public int getRequiredPassNumber() {
    return 1;
  }
}
