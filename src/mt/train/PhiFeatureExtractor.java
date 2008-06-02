package mt.train;

import edu.stanford.nlp.util.Index;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;

import mt.base.DynamicIntegerArrayIndex;
import mt.base.IString;
import mt.base.Sequence;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Extractor for p(e|f), one of the five base feature functions of Moses/Pharaoh.
 * 
 * @author Michel Galley
 */
public class PhiFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugPhiFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  double phiFilter = 0.0;
  final DynamicIntegerArrayIndex lexIndex = new DynamicIntegerArrayIndex();
  final Index fLexIndex = new Index(), eLexIndex = new Index();

  IntArrayList feCounts = new IntArrayList();
  IntArrayList fCounts = new IntArrayList();

  public void init(Properties prop, Index<String> featureIndex, AlignmentTemplates alTemps) {
    super.init(prop, featureIndex, alTemps);
    phiFilter = Double.parseDouble
      (prop.getProperty(CombinedFeatureExtractor.PTABLE_PHI_FILTER_OPT,"-1e30"));
    System.err.printf("minimum p(e|f) = %.3f\n", phiFilter);
  }

  public int getRequiredPassNumber() { return 1; }

  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    // Code below will only get executed during the last pass:
    if(getCurrentPass()+1 == getRequiredPassNumber()) {
      if(DEBUG_LEVEL >= 2)
        System.err.println("Adding phrase to table: "+alTemp.f().toString(" ")+" -> "+alTemp.e().toString(" "));
      // Increment phrase counts c(f,e), c(f), c(e):
      addCountToArray(feCounts, alTemp.getKey());
      addCountToArray(fCounts, alTemp.getFKey());
      if(DEBUG_LEVEL >= 2)
        System.err.printf("Assigned IDs: key=%d fKey=%d eKey=%d\n",alTemp.getKey(), alTemp.getFKey(), alTemp.getEKey());
    }
  }

  /**
   * Print the five translation model features that appear in Moses' phrase tables.
   */
  public Object score(AlignmentTemplate alTemp) {
    // print phi(f|e), lex(f|e), phi(e|f), lex(e|f), and phrase penalty:
    int idx = alTemp.getKey();
    int idxF = alTemp.getFKey();
    assert(idx >= 0 && idxF >= 0);
    assert(idx < feCounts.size());
    // Compute phi features p(e|f):
    double phi_e_f = feCounts.get(idx)*1.0/fCounts.get(idxF);
    // Determine if need to filter phrase:
    if(phiFilter > phi_e_f)
      return null;
    return new double[] { };
  }

  @SuppressWarnings("unchecked")
  private int indexOfLex(IString f, IString e, boolean add)
  { return lexIndex.indexOf(new int[] {f.getId(), e.getId()}, add); }

  @SuppressWarnings("unchecked")
  private int indexOfFLex(IString f, boolean add)
  { return fLexIndex.indexOf(f.getId(), add); }

  private static void addCountToArray(IntArrayList list, int idx) {
    if(idx < 0)
      return;
    while(idx >= list.size())
      list.add(0);
    int newCount = list.get(idx)+1;
    list.set(idx,newCount);
    if(DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx="+idx+" in vector ("+list+").");
  }
}
