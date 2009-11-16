package mt.train;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import mt.base.DynamicIntegerArrayIndex;
import mt.base.Sequence;
import mt.base.Sequences;
import mt.base.IString;
import mt.base.TrieIntegerArrayIndex;

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

  private TrieIntegerArrayIndex fTrieIndex = new TrieIntegerArrayIndex(0);

  private final ArrayList<Int2IntArrayMap> aCounter = new ArrayList<Int2IntArrayMap>();

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
  public void addToIndex(AlignmentTemplate alTemp) {
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
  public void addToIndexIfInDev(AlignmentTemplate alTemp) {
    int fKey = indexOfF(alTemp,false);
    boolean add = (fKey >= 0);
    addToIndex(alTemp,add);
  }

  /**
   * Add source-language phrase to index.
   */
  public void addForeignPhraseToIndex(Sequence<IString> f) {
    fIndex.indexOf(Sequences.toIntArray(f), true);
  }

  public void addForeignPhraseToIndex(int[] f) {
    fIndex.indexOf(f, true);
  }

  /**
   * Increment count for a given alignment for a given phrase-pair.
   */
  public void incrementAlignmentCount(AlignmentTemplate alTemp) {
    if(FILL_HASH && storeAlignmentCounts) {
      int idx = alTemp.getKey();
      int alIdx = alTemp.getAKey();
      final Int2IntArrayMap aCounts;
      if(idx >= 0) {
        assert(idx <= index.size());
        synchronized(aCounter) {
          //assert(idx <= aCounter.size());
          while(idx >= aCounter.size())
            aCounter.add(new Int2IntArrayMap());
          aCounts = aCounter.get(idx);
        }
        synchronized(aCounts) {
          assert(aCounts != null);
          int oldCount = aCounts.get(alIdx);
          if(oldCount < Integer.MAX_VALUE)
            aCounts.put(alIdx, 1+oldCount);
        }
      }
    }
  }

  /**
   * Get the alignment template indexed by idx.
   * When {@link AlignmentTemplate} instances are not
   * kept in memory, this function is the only way
   * to get the alignment template from the index.
   */
  public void reconstructAlignmentTemplate(AlignmentTemplate alTemp, int idx) {
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
    buf.append("ptable=");
    buf.append(index.size());
    buf.append(", f-ptable=");
    buf.append(fIndex.size());
    buf.append(", e-ptable=");
    buf.append(eIndex.size());
    buf.append(", palignments=");
    buf.append(aIndex.size());
    buf.append(", a-counts=");
    buf.append(aCounter.size());
    return buf.toString();
  }

  private void addToIndex(AlignmentTemplate alTemp, boolean add) {
    if(FILL_HASH) {
      double fertility = alTemp.e().size()/alTemp.f().size();
      if(fertility > maxFertility)
        add = false;

      int idxF = indexOfF(alTemp, add);
      int idxE = indexOfE(alTemp, add);

      alTemp.setKey(index.indexOf(new int[]{idxF, idxE}, add));
      alTemp.setFKey(idxF);
      alTemp.setEKey(idxE);
      alTemp.setAKey(indexOfA(alTemp,add));
    }
  }

  public void updateTrieIndex() {
    System.err.println("Updating trie index: "+fIndex.size());
    fTrieIndex = new TrieIntegerArrayIndex(0);
    for (int i=0; i<fIndex.size(); ++i) {
      fTrieIndex.indexOf(fIndex.get(i), true);
      //System.err.println("adding: "+ Arrays.toString(IStrings.toStringArray(fIndex.get(i))));
    }
    System.err.println("Updating trie index: done.");
  }

  public TrieIntegerArrayIndex getTrieIndex() {
    return fTrieIndex;
  }

  /**
   * Return index for source-language phrase in alTemp.
   */
  private int indexOfF(AlignmentTemplate alTemp, boolean add)
  { return fIndex.indexOf(Sequences.toIntArray(alTemp.f()), add); }

  /**
   * Return the source-language phrase indexed by idx.
   */
  public int[] getF(int idx) { return fIndex.get(idx); }
  public int sizeF() { return fIndex.size(); }

  /**
   * Return index for target-language phrase in alTemp.
   */
  private int indexOfE(AlignmentTemplate alTemp, boolean add)
  { return eIndex.indexOf(Sequences.toIntArray(alTemp.e()), add); }

  /**
   * Return the target-language phrase indexed by idx.
   */
  public int[] getE(int idx) { return eIndex.get(idx); }
  public int sizeE() { return eIndex.size(); }

  /**
   * Return index for phrase alignment in alTemp.
   */
  private int indexOfA(AlignmentTemplate alTemp, boolean add)
  { return aIndex.indexOf(alTemp.getCompactAlignment(), add); }

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
