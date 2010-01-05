package edu.stanford.nlp.mt.train;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import edu.stanford.nlp.mt.base.IString;

/**
 * Extractor for printing the number of occurrences of each alignment template.
 * 
 * @author Michel Galley
 */
public class DTUFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugDTUFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  private static final double EXP_M1 = Math.exp(-1);

  @Override
	public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {}

  @Override
	public Object score(AlignmentTemplate alTemp) {
    boolean hasGap = false;
    for(IString w : alTemp.f()) {
      if(w.id == DTUPhraseExtractor.GAP_STR.id) {
        hasGap = true;
        break;
      }
    }
    return new double[] { (hasGap ? EXP_M1 : 1.0) };
  }

  @Override
  public int getRequiredPassNumber() { return 1; }
}
