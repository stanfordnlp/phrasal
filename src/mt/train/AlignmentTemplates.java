package mt.train;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import mt.base.DynamicIntegerArrayIndex;
import mt.base.Sequence;
import mt.base.Sequences;

import edu.stanford.nlp.util.IString;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
// Int2IntLinkedOpenHashMap is second choice
// (overhead caused by this hashmap is relatively small)

/**
 * AlignmentTemplates is a collection that maps between {@link AlignmentTemplate}
 * instances and a contiguous non-negative integer index series beginning (inclusively)
 * at 0. This class supports constant-time lookup in both directions
 * (via get(int) and indexOf(AlignmentTemplate)). Note that get(int) is a relatively
 * costly operation, since, due to memory constraints, AlignmentTemplate instances are
 * not stored in the Collection (hence, an new instance of AlignmentTemplate is
 * constructed on each call of get()).
 *
 * @author Michel Galley
 */
public class AlignmentTemplates extends AbstractCollection<AlignmentTemplate> {

  public static final String DEBUG_PROPERTY = "DebugAlignmentTemplate";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String FILL_HASH_PROPERTY = "FillHash";
  public static final boolean FILL_HASH = Boolean.parseBoolean(System.getProperty(FILL_HASH_PROPERTY, "true"));

  private final DynamicIntegerArrayIndex
     index = new DynamicIntegerArrayIndex(), 
     aIndex = new DynamicIntegerArrayIndex(),
     fIndex = new DynamicIntegerArrayIndex(), 
     eIndex = new DynamicIntegerArrayIndex();

  private ArrayList<Int2IntArrayMap> aCounter = new ArrayList<Int2IntArrayMap>();

  private boolean storeAlignmentCounts = false;
  private boolean filterFromDev = false;
  private double maxFertility = Double.MAX_VALUE;

  public AlignmentTemplates() {}

  /**
   * Initialize alignment template table with a specified max fertility.
   */
  public AlignmentTemplates(Properties prop, boolean filterFromDev) {
    this.maxFertility = Double.parseDouble
      (prop.getProperty(CombinedFeatureExtractor.MAX_FERTILITY_OPT,"1e30"));
    this.filterFromDev = filterFromDev;
  }

  public void enableAlignmentCounts(boolean storeAlignmentCounts) {
    this.storeAlignmentCounts = storeAlignmentCounts;
    System.err.println("AlignmentTemplates: some feature extractor requires alignment counts.");
  }

  /**
   * Add alignment template to phrase table. 
   */
  public synchronized void addToIndex(AlignmentTemplate alTemp) {
    if(filterFromDev) {
      int fKey = indexOfF(alTemp,false);
      boolean add = (fKey >= 0);
      addToIndex(alTemp,add);
    } else {
      addToIndex(alTemp,true);
    }
  }

  /**
   * Add alignment template to phrase table if the source-language phrase is in the dev corpus. 
   */
  public synchronized void addToIndexIfInDev(AlignmentTemplate alTemp) {
    int fKey = indexOfF(alTemp,false);
    boolean add = (fKey >= 0);
    addToIndex(alTemp,add);
  }

  /**
   * Add source-language phrase to index.
   */
  public synchronized void addForeignPhraseToIndex(Sequence<IString> f) {
    fIndex.indexOf(Sequences.toIntArray(f), true);
  }

  /**
   * Increment count for a given alignment for a given phrase-pair.
   */
  @SuppressWarnings("unchecked")
  public synchronized void incrementAlignmentCount(AlignmentTemplate alTemp) {
    if(FILL_HASH && storeAlignmentCounts) {
      int idx = alTemp.getKey();
      int alIdx = alTemp.getAKey();
      if(idx >= 0) {
        assert(idx <= index.size());
        assert(idx <= aCounter.size());
        if(idx == aCounter.size())
          aCounter.add(new Int2IntArrayMap());
        Int2IntArrayMap aCounts = aCounter.get(idx);
        assert(aCounts != null);
        int oldCount = aCounts.get(alIdx);
        if(oldCount < Integer.MAX_VALUE)
          aCounts.put(alIdx, 1+oldCount);
      }
    }
  }

  /**
   * Get the alignment template indexed by idx.
   * When {@link AlignmentTemplate} instances are not
   * kept in memory, this function is the only way
   * to get the alignment template from the index.
   */
  public synchronized void reconstructAlignmentTemplate(AlignmentTemplate alTemp, int idx) {
    int[] idxInts = index.get(idx);
    int[] idxIntsF = fIndex.get(idxInts[0]);
    int[] idxIntsE = eIndex.get(idxInts[1]);
    int aIdx = getArgmaxAlignment(idx);
    int[] idxIntsA = (aIdx >= 0) ? aIndex.get(aIdx) : new int[] {};
    alTemp.init(idxIntsF,idxIntsE,idxIntsA,false);
    // Let the alignment template know its keys:
    alTemp.setKey(idx);
    alTemp.setFKey(idxInts[0]);
    alTemp.setEKey(idxInts[1]);
    alTemp.setAKey(aIdx);
  }

  @Override
	public Iterator<AlignmentTemplate> iterator()
  { 
  	throw new UnsupportedOperationException();
  }
  	

  @Override
	public int size()
  { return index.size(); }

  public String getSizeInfo() {
    StringBuffer buf = new StringBuffer();
    buf.append("ptable="+index.size());
    buf.append(", f-ptable="+fIndex.size());
    buf.append(", e-ptable="+eIndex.size());
    buf.append(", palignments="+aIndex.size());
    buf.append(", a-counts="+aCounter.size());
    return buf.toString();
  }

  private void addToIndex(AlignmentTemplate alTemp, boolean add) {
    if(FILL_HASH) {
      double fertility = alTemp.e().size()/alTemp.f().size();
      if(fertility > maxFertility)
        add = false;
      alTemp.setKey(indexOf(alTemp,add));
      alTemp.setFKey(indexOfF(alTemp,add));
      alTemp.setEKey(indexOfE(alTemp,add));
      alTemp.setAKey(indexOfA(alTemp,add));
    }
  }

  /**
   * Return index for alTemp.
   */
  @SuppressWarnings("unchecked")
  private int indexOf(AlignmentTemplate alTemp, boolean add)
  { return index.indexOf(new int[] {indexOfF(alTemp, add), indexOfE(alTemp, add)}, add); }

  /**
   * Return index for source-language phrase in alTemp.
   */
  @SuppressWarnings("unchecked")
  private int indexOfF(AlignmentTemplate alTemp, boolean add)
  { return fIndex.indexOf(Sequences.toIntArray(alTemp.f()), add); }

  /**
   * Return the source-language phrase indexed by idx.
   */
  public synchronized int[] getF(int idx) { return fIndex.get(idx); }
  public int sizeF() { return fIndex.size(); }

  /**
   * Return index for target-language phrase in alTemp.
   */
  @SuppressWarnings("unchecked")
  private int indexOfE(AlignmentTemplate alTemp, boolean add)
  { return eIndex.indexOf(Sequences.toIntArray(alTemp.e()), add); }

  /**
   * Return the target-language phrase indexed by idx.
   */
  public synchronized int[] getE(int idx) { return eIndex.get(idx); }
  public int sizeE() { return eIndex.size(); }

  /**
   * Return index for phrase alignment in alTemp.
   */
  @SuppressWarnings("unchecked")
  private int indexOfA(AlignmentTemplate alTemp, boolean add)
  { return aIndex.indexOf(alTemp.getCompactAlignment(), add); }

  @SuppressWarnings("unchecked")
  private int getArgmaxAlignment(int idx) {
    if(idx >= aCounter.size())
      return -1;
    // Linear search:
    Int2IntArrayMap aCounts = aCounter.get(idx);
    int maxK = -1;
    int maxV = Integer.MIN_VALUE;
    String maxKLex = null;
    for(int k : aCounts.keySet()) {
      int v = aCounts.get(k);
      if(v == maxV) {
        // If there is a tie, take lexicographic order as defined in Moses:
        String kLex = AlignmentTemplate.alignmentToString(aIndex.get(k));
        if(maxKLex == null)
          maxKLex = AlignmentTemplate.alignmentToString(aIndex.get(maxK));
        if(kLex.compareTo(maxKLex) < 0) {
          maxK = k;
          maxV = v;
          maxKLex = kLex;
        }
      } else if(v > maxV) {
        maxK = k;
        maxV = v;
        maxKLex = null;
      }
    }
    assert(maxK >= 0);
    return maxK;
  }
}
