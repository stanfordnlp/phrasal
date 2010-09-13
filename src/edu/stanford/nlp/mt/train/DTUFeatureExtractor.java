package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.DTUTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.IntPair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class DTUFeatureExtractor extends MosesFeatureExtractor {

  private static final String DEBUG_PROPERTY = "DebugDTUFeatureExtractor";
  private static final int DEBUG = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  private static final String LAPLACE_PROPERTY = "LaplaceSmoothing";
  private static final float LAPLACE = Float.parseFloat(System.getProperty(LAPLACE_PROPERTY, "0.5f"));

  private static final String SHARE_SIZE_COUNTS_PROPERTY = "shareSizeCounts";
  private static final boolean SHARE_SIZE_COUNTS = Boolean.parseBoolean(System.getProperty(SHARE_SIZE_COUNTS_PROPERTY, "true"));

  static { System.err.printf("Share size counts: %s\n", SHARE_SIZE_COUNTS); }

  final List<int[][]> gapSizeCountsF = new ArrayList<int[][]>();
  final List<int[][]> gapSizeCountsE = new ArrayList<int[][]>();

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    super.featurizePhrase(alTemp, alGrid);
    
    if (alTemp instanceof DTUInstance)  {
      DTUInstance dtu = (DTUInstance) alTemp;
      updateGapSizeStats(dtu.fSet, SHARE_SIZE_COUNTS ? alTemp.fKey : alTemp.key, gapSizeCountsF);
      updateGapSizeStats(dtu.eSet, SHARE_SIZE_COUNTS ? alTemp.eKey : alTemp.key, gapSizeCountsE);
    }
  }

  public static List<Integer> getBins(CoverageSet cs) {
    IntPair previousSeg, currentSeg = null;
    List<Integer> bins = new ArrayList<Integer>();
    // Compute gap sizes:
    for (Iterator<IntPair> it = cs.getSegmentIterator(); it.hasNext(); ) {
      previousSeg = currentSeg;
      currentSeg = it.next();
      //System.err.printf("seg: %d-%d\n", currentSeg.getSource(), currentSeg.getTarget());
      if (previousSeg != null) {
        int s = currentSeg.getSource()-previousSeg.getTarget()-1;
        bins.add(sizeToBin(s));
      }
    }
    return bins;
  }

  private static void updateGapSizeStats(CoverageSet cs, int key, List<int[][]> countList) {

    if (key < 0) return;

    List<Integer> binCounts = new LinkedList<Integer>();

    for (int binId : getBins(cs))
      binCounts.add(binId);

    // Resize list as needed:
    synchronized (countList) {
      while (key >= countList.size())
        countList.add(null);
    }

    // Get count corresponding to key:
    int[][] count = countList.get(key);

    // If needed, initialize count:
    if (count == null) {
      count = new int[binCounts.size()][4];
      countList.set(key, count);
    }

    for (int i=0; i<binCounts.size(); ++i) {
      int bi = binCounts.get(i);
      ++count[i][bi];
    }
  }

  private static final int BINS = 4; // Note: number must reflect sizeToBin

  private static int sizeToBin(int sz) {
    if (sz <= 2) return 0;
    if (sz <= 4) return 1;
    if (sz <= 8) return 2;
    return 3;
  }

  @Override
  public String toString(AlignmentTemplateInstance p, boolean withAlignment) {
    StringBuilder buf = new StringBuilder();
    addToken(buf, SHARE_SIZE_COUNTS ? p.fKey : p.key, p.f, gapSizeCountsF);
    buf.append(AlignmentTemplate.DELIM);
    addToken(buf, SHARE_SIZE_COUNTS ? p.eKey : p.key, p.e, gapSizeCountsE);
    if (withAlignment)
      p.addAlignmentString(buf);
    return buf.toString();
  }

  private static void addToken(StringBuilder buf, int key, Sequence<IString> seq, List<int[][]> gapSizeCounts) {

    int gapId = -1;

    for (int tokI=0; tokI<seq.size(); ++tokI) {
      if (tokI>0) buf.append(" ");
      IString s = seq.get(tokI);
      boolean addStats = s.equals(DTUTable.GAP_STR);
      buf.append(s);
      if (addStats) {
        ++gapId;
        int[] counts = gapSizeCounts.get(key)[gapId];
        buf.append("[");
        float n = ArrayMath.sum(counts) + counts.length* LAPLACE;
        for (int binI=0; binI<counts.length; ++binI) {
          if (binI>0)
            buf.append(",");
          buf.append((counts[binI]*1.0f+ LAPLACE)/n);
        }
        buf.append("]");
      }
    }
  }
}
