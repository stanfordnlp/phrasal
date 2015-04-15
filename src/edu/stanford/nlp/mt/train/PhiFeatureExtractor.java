package edu.stanford.nlp.mt.train;

import java.util.Properties;

import edu.stanford.nlp.util.Index;

import com.google.common.collect.ConcurrentHashMultiset;

/**
 * Extractor for p(e|f), one of the five base feature functions of
 * Moses/Pharaoh.
 * 
 * @author Michel Galley
 */
public class PhiFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugPhiFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));

  double phiFilter = 0.0;

  ConcurrentHashMultiset<Integer> feCounts = ConcurrentHashMultiset.create();
  ConcurrentHashMultiset<Integer> fCounts = ConcurrentHashMultiset.create();

  @Override
  public void init(Properties prop, Index<String> featureIndex,
      AlignmentTemplates alTemps) {
    super.init(prop, featureIndex, alTemps);
    phiFilter = Double.parseDouble(prop.getProperty(
        PhraseExtract.PTABLE_PHI_FILTER_OPT, "-1e30"));
    System.err.printf("minimum phi(e|f) = %.5f\n", phiFilter);
  }

  @Override
  public int getRequiredPassNumber() {
    return 1;
  }

  @Override
  public void featurizeSentence(SymmetricalWordAlignment sent, AlignmentGrid alGrid) {
  }

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
    // Code below will only get executed during the last pass:
    if (getCurrentPass() + 1 == getRequiredPassNumber()) {
      if (DEBUG_LEVEL >= 2)
        System.err.println("Adding phrase to table: "
            + alTemp.f().toString(" ") + " -> " + alTemp.e().toString(" "));
      // Increment phrase counts c(f,e), c(f), c(e):
      addCountToArray(feCounts, alTemp.getKey());
      addCountToArray(fCounts, alTemp.getFKey());
      if (DEBUG_LEVEL >= 2)
        System.err.printf("Assigned IDs: key=%d fKey=%d eKey=%d\n",
            alTemp.getKey(), alTemp.getFKey(), alTemp.getEKey());
    }
  }

  /**
   * Print the five translation model features that appear in Moses' phrase
   * tables.
   */
  @Override
  public Object score(AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    int idxF = alTemp.getFKey();
    assert (idx >= 0 && idxF >= 0);
    assert (idx < feCounts.size());
    // Compute phi features p(e|f):
    double phi_e_f = feCounts.count(idx) * 1.0 / fCounts.count(idxF);
    // Determine if need to filter phrase:
    if (phiFilter > phi_e_f)
      return null;
    return new double[] {};
  }

  private static void addCountToArray(ConcurrentHashMultiset<Integer> counter, int idx) {
    if (idx < 0)
      return;
    counter.add(idx);
    if (DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx=" + idx + " in vector ("
          + counter + ").");
  }
}
