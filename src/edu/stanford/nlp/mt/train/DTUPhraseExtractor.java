package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TrieIntegerArrayIndex;
import edu.stanford.nlp.util.Triple;

import java.util.*;

/**
 * @author Michel Galley
 */
public class DTUPhraseExtractor extends AbstractPhraseExtractor {

  static public final IString GAP_STR = new IString("X"); // uppercase, so shouldn't clash with other symbols

  static public final String MAX_SPAN_OPT = "maxDTUSpan";
  static public final String MAX_SPAN_E_OPT = "maxDTUSpanE";
  static public final String MAX_SPAN_F_OPT = "maxDTUSpanF";

  static public final String MAX_SIZE_OPT = "maxDTUSize";
  static public final String MAX_SIZE_E_OPT = "maxDTUSizeE";
  static public final String MAX_SIZE_F_OPT = "maxDTUSizeF";

  static public final String NO_TARGET_GAPS_OPT  = "noTargetGaps";
  static public final String NAACL2010_OPT = "naacl2010";
  static public final String GAPS_BOTH_SIDES_OPT = "gapsBothSides";
  static public final String GROW_SOURCE_OPT = "growSource";
  static public final String LOOSE_DISC_PHRASES_OPT = "looseDTU"; // include loose (i.e., not only tight) discontinuous phrases
  static public final String LOOSE_DISC_PHRASES_OUTSIDE_OPT = "looseOutsideDTU"; // include loose (i.e., not only tight) discontinuous phrases
  static public final String NO_UNALIGNED_GAPS_OPT = "noUnalignedGaps"; // do not extract "w X w" if X is unaligned
  static public final String NO_UNALIGNED_OR_LOOSE_GAPS_OPT = "noUnalignedOrLooseGaps"; // do not extract w X w unless the first and last words of X are unaligned
  static public final String NO_UNALIGNED_SUBPHRASE_OPT = "noUnalignedSubphrase";
  static public final String ONLY_CROSS_SERIAL_OPT  = "onlyCrossSerialDTU";
  static public final String HIERO_OPT  = "hieroDTU";

  /*
  static public final String ONLY_CROSS_SERIAL_OPT  = "onlyCrossSerialDTU";
  static public final String NO_CROSS_SERIAL_PHRASES_OPT  = "noCrossSerialPhrases";
  */

  static public final int DEFAULT_MAX_SIZE = 5;
  static public final int DEFAULT_MAX_SPAN = 12;

  static int maxSize = DEFAULT_MAX_SIZE;
  static int maxSizeF = maxSize, maxSizeE = maxSize;

  static int maxSpan = DEFAULT_MAX_SPAN;
  static int maxSpanF = maxSpan, maxSpanE = maxSpan;

  static final int QUEUE_SZ = 1024;

  static final boolean DEBUG = System.getProperty("DebugDTU") != null;


  static boolean withGaps, looseDTU, looseOutsideDTU, hieroDTU, naacl2010, gapsBothSides,
       noTargetGaps, noUnalignedSubphrase, noUnalignedGaps, noUnalignedOrLooseGaps, growSource;

  Set<DTUPhrase> seen = new HashSet<DTUPhrase>(QUEUE_SZ);
  BitSet unalignedWordsE, unalignedWordsF;

  MosesPhraseExtractor substringExtractor;

  public DTUPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    substringExtractor = new MosesPhraseExtractor(prop, alTemps, extractors);
    substringExtractor.alGrid = alGrid;
    System.err.println("Using DTU phrase extractor.");
  }

  public Object clone() throws CloneNotSupportedException {
    DTUPhraseExtractor c = (DTUPhraseExtractor) super.clone();
    c.substringExtractor = (MosesPhraseExtractor) substringExtractor.clone();
    c.substringExtractor.alGrid = c.alGrid;
    c.seen = new HashSet<DTUPhrase>(QUEUE_SZ);
    return c;
  }

  static String stringWithGaps(Sequence<IString> f, CoverageSet bitset) {
    StringBuilder sb = new StringBuilder();
    int oldI = -1;
    for (int i : bitset) {
      if (oldI != -1) {
        if (i > oldI+1) {
          sb.append(" ").append(DTUPhraseExtractor.GAP_STR).append(" ");
        } else {
          sb.append(" ");
        }
      }
      sb.append(f.get(i));
      oldI = i;
    }
    return sb.toString();
  }

  static public void setDTUExtractionProperties(Properties prop) {

    String optStr;
    withGaps = true; // withGaps normally set to true. (withGaps = false only useful for debugging)

    // No gaps on the target:
    optStr = prop.getProperty(NO_TARGET_GAPS_OPT);
    noTargetGaps = optStr != null && !optStr.equals("false");

    // Ignore DTU if a gap only covers unaligned words:
    optStr = prop.getProperty(NO_UNALIGNED_GAPS_OPT);
    noUnalignedGaps = optStr != null && !optStr.equals("false");

    // Ignore DTU if first or last word of X is unaligned (same as Hiero):
    optStr = prop.getProperty(NO_UNALIGNED_OR_LOOSE_GAPS_OPT);
    noUnalignedOrLooseGaps = optStr != null && !optStr.equals("false");

    // Each wi in w1 X w2 X ... X wN must be licensed by at least one alignment:
    optStr = prop.getProperty(NO_UNALIGNED_SUBPHRASE_OPT);
    noUnalignedSubphrase = optStr != null && !optStr.equals("false");

    // Configuration of NAACL2010:
    optStr = prop.getProperty(NAACL2010_OPT);
    naacl2010 = optStr != null && !optStr.equals("false");

    // Enable phrases with gaps on both sides:
    // (note: this creates a lot of phrases)
    optStr = prop.getProperty(GAPS_BOTH_SIDES_OPT);
    gapsBothSides = optStr != null && !optStr.equals("false");

    optStr = prop.getProperty(GROW_SOURCE_OPT);
    growSource = optStr != null && !optStr.equals("false");

    // Tight or loose discontinuous phrases?
    optStr = prop.getProperty(LOOSE_DISC_PHRASES_OPT);
    looseDTU = optStr != null && !optStr.equals("false");
    optStr = prop.getProperty(LOOSE_DISC_PHRASES_OUTSIDE_OPT);
    looseOutsideDTU = optStr != null && !optStr.equals("false");

    String s;
    if ((s = prop.getProperty(MAX_SPAN_F_OPT)) != null) {
      maxSpanF = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SPAN_OPT)) != null) {
      maxSpanF = Integer.parseInt(s);
    }

    if ((s = prop.getProperty(MAX_SPAN_E_OPT)) != null) {
      maxSpanE = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SPAN_OPT)) != null) {
      maxSpanE = Integer.parseInt(s);
    }
    maxSpan = Math.max(maxSpanF, maxSpanE);

    if ((s = prop.getProperty(MAX_SIZE_F_OPT)) != null) {
      maxSizeF = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SIZE_OPT)) != null) {
      maxSizeF = Integer.parseInt(s);
    }

    if ((s = prop.getProperty(MAX_SIZE_E_OPT)) != null) {
      maxSizeE = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SIZE_OPT)) != null) {
      maxSizeE = Integer.parseInt(s);
    }
    maxSize = Math.max(maxSizeF, maxSizeE);
    System.err.printf
     ("discontinuous phrase constraints:"+
      "size: max=%d,maxE=%d,maxF=%d span: max=%d,maxE=%d,maxF=%d\n",
        maxSize, maxSizeE, maxSizeF,
        maxSpan, maxSpanE, maxSpanF);

    // Roughly the same set of phrases as Hiero:
    optStr = prop.getProperty(HIERO_OPT);
    hieroDTU = optStr != null && !optStr.equals("false");
    if (hieroDTU) {
      looseDTU = true;
      looseOutsideDTU = true;
      noUnalignedOrLooseGaps = true;
      //noCrossSerialPhrases = true;
    }

    if (DEBUG)
      AlignmentTemplateInstance.lazy = false;
  }

  class DTUPhrase {

    WordAlignment sent;

    CoverageSet consistencizedF, consistencizedE;

    final CoverageSet f, e;

    boolean isContiguous(BitSet bitset) {
      int i = bitset.nextSetBit(0);
      int j = bitset.nextClearBit(i+1);
      return (bitset.nextSetBit(j+1) == -1);
    }

    BitSet e() { return e; }
    BitSet f() { return f; }

    boolean fContiguous() { return isContiguous(f); }
    boolean eContiguous() { return isContiguous(e); }

    DTUPhrase expandF(int fi) { f.set(fi); return consistencize(fi, true) ? this : null; }
    DTUPhrase expandE(int ei) { e.set(ei); return consistencize(ei, false) ? this : null; }

    boolean sane() { return f.equals(consistencizedF) && e.equals(consistencizedE); }

    int sizeE() { return e.cardinality(); }
    int sizeF() { return f.cardinality(); }

    int spanE() { return e.length()-e.nextSetBit(0); }
    int spanF() { return f.length()-f.nextSetBit(0); }

    void addE(int ei) { e.set(ei); }
    void addF(int fi) { f.set(fi); }

    CoverageSet toConsistencizeInF() {
      CoverageSet newF = f.clone();
      newF.xor(consistencizedF);
      return newF;
    }

    CoverageSet toConsistencizeInE() {
      CoverageSet newE = e.clone();
      newE.xor(consistencizedE);
      return newE;
    }

    @Override
    public String toString() {
      return toString(true);
    }

    String toString(boolean sanityCheck) {
      if (sanityCheck && !sane()) {
        System.err.println("f: "+f);
        System.err.println("cf: "+consistencizedF);
        System.err.println("e: "+e);
        System.err.println("ce: "+consistencizedE);
      }
      String fp = stringWithGaps(sent.f(), f);
      String ep = stringWithGaps(sent.e(), e);
      return String.format("f={{{%s}}} e={{{%s}}}", fp, ep);
    }

    @Override
    public int hashCode() {
      // Same implementation as in Arrays.hashCode():
      return 31 * f.hashCode() + e.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof DTUPhrase))
        return false;
      DTUPhrase dtu = (DTUPhrase) o;
      return f.equals(dtu.f) && e.equals(dtu.e);
    }

    DTUPhrase(DTUPhrase dtu) {
      sent = dtu.sent;
      f = dtu.f.clone();
      e = dtu.e.clone();
      consistencizedF = dtu.consistencizedF.clone();
      consistencizedE = dtu.consistencizedE.clone();
    }

    DTUPhrase(WordAlignment sent, int fi, int ei) {
      super();
      this.sent = sent;
      f = new CoverageSet();
      e = new CoverageSet();
      f.set(fi);
      e.set(ei);
      consistencizedF = new CoverageSet();
      consistencizedE = new CoverageSet();
    }

    DTUPhrase(WordAlignment sent, CoverageSet f, CoverageSet e) {
      super();
      this.sent = sent;
      this.f = f;
      this.e = e;
      consistencizedF = new CoverageSet();
      consistencizedE = new CoverageSet();
    }

    boolean isConsistencizedE(int ei) { return consistencizedE.get(ei); }
    boolean isConsistencizedF(int fi) { return consistencizedF.get(fi); }

    void setConsistencizedE(int ei) { consistencizedE.set(ei); }
    void setConsistencizedF(int fi) { consistencizedF.set(fi); }

    boolean consistencize(int i, boolean source) {
      if (!consistencize(i, source, true))
        return false;

      //Drop phrase if too large:
      if (sizeF() > maxSize) return false;
      if (sizeE() > maxSize) return false;
      if (!fContiguous() || !eContiguous()) {
        if(sizeF() > maxSize) return false;
        if(sizeE() > maxSize) return false;
        if(spanF() > maxSpan) return false;
        if(spanE() > maxSpan) return false;
      }
      return true;
    }

    boolean consistencize(int i, boolean source, boolean topLevel) {
      if (sizeF() > maxSize) return false;
      if (sizeE() > maxSize) return false;

      if (source) {
        setConsistencizedF(i);
        for (int ei : sent.f2e(i)) {
          if (!isConsistencizedE(ei)) {
            addE(ei);
            if (!consistencize(ei, false, false))
              return false;
          }
        }
      } else {
        setConsistencizedE(i);
        for (int fi : sent.e2f(i)) {
          if (!isConsistencizedF(fi)) {
            addF(fi);
            if (!consistencize(fi, true, false))
              return false;
          }
        }
      }

      // Consistencize words that have been added, but that may not be consistent:
      if (topLevel) {
        for (int fi : toConsistencizeInF()) {
          if (!consistencize(fi, true, true))
            return false;
        }
        for (int ei : toConsistencizeInE()) {
          if (!consistencize(ei, false, true))
            return false;
        }
      }
      return true;
    }

    boolean hasUnalignedGap() {
      boolean unalignedGap = hasUnalignedGap(sent, f, true) || hasUnalignedGap(sent, e, false);
      if (DEBUG) {
        System.err.println("f: "+sent.f());
        System.err.println("e: "+sent.e());
        System.err.println("fs: "+f);
        System.err.println("es: "+e);
        System.err.println("gap: "+hasUnalignedGap(sent, f, true));
        System.err.println("gap: "+hasUnalignedGap(sent, e, false));
      }
      return unalignedGap;
    }

    boolean hasUnalignedGap(WordAlignment sent, BitSet fs, boolean source) {
      if (fs.isEmpty())
        return false;
      int startIdx, endIdx = 0;
      for (;;) {
        startIdx = fs.nextClearBit(fs.nextSetBit(endIdx));
        endIdx = fs.nextSetBit(startIdx)-1;
        if (startIdx > endIdx)
          break;
        boolean unalignedGap = true;
        for (int i=startIdx; i <= endIdx; ++i) {
          assert (!fs.get(i));
          if (source) {
            if (!sent.f2e(i).isEmpty()) {
              unalignedGap = false;
              break;
            }
          }  else {
            if (!sent.e2f(i).isEmpty()) {
              unalignedGap = false;
              break;
            }
          }
        }
        if (unalignedGap)
         return true;
      }
      return false;
    }

    boolean hasUnalignedSubphrase() {
      return hasUnalignedSubphrase(sent, f, true) || hasUnalignedSubphrase(sent, e, false);
    }

    boolean hasUnalignedSubphrase(WordAlignment sent, BitSet fs, boolean source) {
      int startIdx, endIdx=0;
      for (;;) {
        startIdx = fs.nextSetBit(endIdx);
        if (startIdx < 0)
          break;
        endIdx = fs.nextClearBit(startIdx)-1;
        boolean unalignedSP = true;
        for (int i=startIdx; i <= endIdx; ++i) {
          if (source) {
            if (!sent.f2e(i).isEmpty()) {
              unalignedSP = false;
              break;
            }
          }  else {
            if (!sent.e2f(i).isEmpty()) {
              unalignedSP = false;
              break;
            }
          }
        }
        ++endIdx;
        if (unalignedSP)
         return true;
      }
      return false;
    }

    boolean hasUnalignedOrLooseGap() {
      return hasUnalignedOrLooseGap(sent, f, true) || hasUnalignedOrLooseGap(sent, e, false);
    }

    boolean hasUnalignedOrLooseGap(WordAlignment sent, BitSet fs, boolean source) {
      if (fs.isEmpty())
        return false;
      int startIdx, endIdx = 0;
      for (;;) {
        startIdx = fs.nextClearBit(fs.nextSetBit(endIdx));
        endIdx = fs.nextSetBit(startIdx)-1;
        if (startIdx > endIdx)
          break;
        if (source) {
          if (sent.f2e(startIdx).isEmpty() || sent.f2e(startIdx).isEmpty()) {
            return true;
          }
        }  else {
          if (sent.e2f(endIdx).isEmpty() || sent.e2f(endIdx).isEmpty()) {
            return true;
          }
        }
      }
      return false;
    }

    boolean consistencize() {
      boolean done = false;
      while (!done) {
        boolean doneF=false, doneE=false;
        if (f.equals(consistencizedF)) {
          doneF = true;
        } else {
          for (int fi : f)
            if (!consistencizedF.get(fi))
              if (!consistencize(fi,true))
                return false;
          if (e.isEmpty())
            return false;
        }
        if (e.equals(consistencizedE)) {
          doneE = true;
        } else {
          for (int ei : e)
            if (!consistencizedE.get(ei))
              if (!consistencize(ei,false))
                return false;
          if (f.isEmpty())
            return false;
        }
        done = doneF && doneE;
      }
      return true;
    }

    /*
    DTUPhrase hieroTightness() {
      if (hieroDTU) {
        boolean done = false;
        while (!done) {

          boolean updated = false;

          // source:
          int firstI = f.nextSetBit(0);
          int lastI = f.length()-1;
          for (int i=firstI+1; i<lastI; ++i)
            if (unalignedWordsF.get(i) && !f.get(i)) {
              f.set(i);
              updated = true;
            }

          // target:
          firstI = e.nextSetBit(0);
          lastI = e.length()-1;
          for (int i=firstI+1; i<lastI; ++i)
            if (unalignedWordsE.get(i) && !e.get(i)) {
              e.set(i);
              updated = true;
            }

          if (!updated || !consistencize())
            done = true;
        }
      }
      return this;
    }
    */

    BitSet adjacentWords(BitSet bitset, boolean growOutside) {
      BitSet adjWords = new BitSet();
      int firstI = bitset.nextSetBit(0);
      int lastI = bitset.length()-1;
      int si = 0;
      for (;;) {
        si = bitset.nextSetBit(si);
        if (si > 0) {
          if (growOutside || si > firstI)
            adjWords.set(si-1);
        }
        if (si == -1) break;
        int ei = bitset.nextClearBit(++si);
        if (growOutside || ei <= lastI)
          adjWords.set(ei);
        si = ei;
      }
      return adjWords;
    }

    BitSet candidateIdx(BitSet currentSet, boolean growOutside) {
      BitSet successors = adjacentWords(currentSet, growOutside);
      if (hieroDTU) {
        // TODO: speedup:
        successors.or(unalignedWordsE);
      }
      if (DEBUG) {
        System.err.printf("sent: %s\n",sent);
        System.err.println("dtu to expand: "+this);
        System.err.printf("in: %s\n",currentSet);
        System.err.printf("out: %s\n",successors);
      }
      return successors;
    }

    Collection<DTUPhrase> growDTU(boolean growSource, boolean growInside, boolean growInsideAndOutside) {
      List<DTUPhrase> list = new LinkedList<DTUPhrase>();
      if (growSource) {
        int s=-1;
        if (growInside && sizeF() < maxSize) {
          BitSet successors = candidateIdx(f, growInsideAndOutside && spanF() < maxSpan);
          for (;;) {
            s = successors.nextSetBit(s+1);
            if (s < 0 || s >= sent.f().size()) break;
            list.add(new DTUPhrase(this).expandF(s));
          }
        }
      }
      {
        int s=-1;
        if (growInside && sizeE() < maxSize) {
          BitSet successors = candidateIdx(e, growInsideAndOutside && spanE() < maxSpan);
          for (;;) {
            s = successors.nextSetBit(s+1);
            if (s < 0 || s >= sent.e().size()) break;
            list.add(new DTUPhrase(this).expandE(s));
          }
        }
      }
      return list;
    }
  }

  void growDTUs(Deque<DTUPhrase> q, boolean growSource, boolean growInside, boolean growInsideAndOutside) {
    while (!q.isEmpty()) {
      DTUPhrase dtu = q.pollFirst();
      for (DTUPhrase sp : dtu.growDTU(growSource, growInside, growInsideAndOutside)) {
        if (sp != null && !seen.contains(sp)) {
          q.offerLast(sp);
          seen.add(sp);
        }
      }
    }
  }

  // Extract all subsequences:
  Set<CoverageSet> subsequenceExtract(WordAlignment sent) {

    TrieIntegerArrayIndex fTrieIndex = alTemps.getSourceFilter().getSourceTrie();
    assert(fTrieIndex != null);

    Deque<Triple<Integer,Integer, CoverageSet>> q = new LinkedList<Triple<Integer,Integer, CoverageSet>>();
    Set<CoverageSet> bitsets = new HashSet<CoverageSet>();
    for (int i=0; i < sent.f().size(); ++i) {
      //System.err.println("i: "+i);
      q.clear();
      int startState = TrieIntegerArrayIndex.IDX_ROOT;
      q.offerFirst(new Triple<Integer,Integer, CoverageSet>(i, startState, new CoverageSet()));
      while (!q.isEmpty()) {
        Triple<Integer,Integer, CoverageSet> el = q.pollLast();
        int pos = el.first();
        int curState = el.second();
        CoverageSet bitset = el.third();
        if (!bitset.isEmpty()) {
          bitsets.add(bitset);
          //System.err.printf("bs=%s %s\n",bitset,stringWithGaps(sent.f(),bitset));
        }
        if (pos >= sent.f().size()) continue;
        // Try to match a terminal:
        int nextState = fTrieIndex.getSuccessor(curState, sent.f().get(pos).id);
        if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
          CoverageSet newBitset = bitset.clone();
          newBitset.set(pos);
          q.offerFirst(new Triple<Integer,Integer, CoverageSet>(pos+1,nextState,newBitset));
        }
        // Try to match non terminals:
        int ntNextState = fTrieIndex.getSuccessor(curState, DTUPhraseExtractor.GAP_STR.id);
        if (ntNextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
          int lastPos = i+maxSpan;
          for (int pos2=pos+1; pos2<=lastPos; ++pos2) {
            if (pos2 >= sent.f().size()) break;
            int next2State = fTrieIndex.getSuccessor(ntNextState, sent.f().get(pos2).id);
            if (next2State != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
              CoverageSet newBitset = bitset.clone();
              newBitset.set(pos2);
              q.offerFirst(new Triple<Integer,Integer, CoverageSet>(pos2+1,next2State,newBitset));
            }
          }
        }
      }
    }
    return bitsets;
  }

  protected AlignmentTemplateInstance addPhraseToIndex
      (WordAlignment sent, DTUPhrase dtu, boolean isConsistent) {

    BitSet fs = dtu.f();
    BitSet es = dtu.e();

    boolean fContiguous = dtu.fContiguous();
    boolean eContiguous = dtu.eContiguous();

    // Filter phrases:

    // Ignore if DTU is continuous on both sides (already extracted):
    if (fContiguous && eContiguous) return null;

    // Ignore if DTU is discontinuous on both sides:
    if (!gapsBothSides && !fContiguous && !eContiguous) return null;

    // If gap on only one side, use span and size limits that may not
    // be the same for each side:
    if (!fContiguous) if (maxSpanF < dtu.spanF() || maxSizeF < dtu.sizeF()) return null;
    if (!eContiguous) if (maxSpanE < dtu.spanE() || maxSizeE < dtu.sizeE()) return null;

    // Create dtuTemp:
    DTUInstance dtuTemp = new DTUInstance(sent, fs, es, fContiguous, eContiguous);
    alGrid.addAlTemp(dtuTemp, isConsistent);

    alTemps.addToIndex(dtuTemp);
    alTemps.incrementAlignmentCount(dtuTemp);

    return dtuTemp;
  }

  @Override
  public void extractPhrases(WordAlignment sent) {

    if (DEBUG) {
      System.err.println("f: "+sent.f());
      System.err.println("e: "+sent.e());
      System.err.println("a: "+sent.toString());
    }

    int fSize = sent.f().size();
    int eSize = sent.e().size();

    alGrid.init(sent);
    if (fSize < PRINT_GRID_MAX_LEN && eSize < PRINT_GRID_MAX_LEN)
      alGrid.printAlTempInGrid("line: "+sent.getId(),null,System.err);

    unalignedWordsF = sent.unalignedF();
    unalignedWordsE = sent.unalignedE();

    seen.clear();

    // Add contiguous phrases:
    substringExtractor.addPhrasesToGrid(sent);

    if (naacl2010) {
      // (A) Add minimal translation units:
      Deque<DTUPhrase> minimalUnitsQueue = new LinkedList<DTUPhrase>();
      for (int fi=0; fi<sent.f().size(); ++fi) {
        for (int ei : sent.f2e(fi)) {
          DTUPhrase p = new DTUPhrase(sent, fi, ei);
          if (p.consistencize(fi, true)) {
            if (!seen.contains(p)) {
              minimalUnitsQueue.offerLast(p);
              seen.add(p);
            }
          }
        }
      }
      growDTUs(minimalUnitsQueue, true, true, true);
    }

    {
      // (B) Add discontinuous translation units that match input string:
      // Q: Why have both (A) and (B)?
      // A: so that
      Deque<DTUPhrase> matchQueue = new LinkedList<DTUPhrase>();
      for (CoverageSet bitset : subsequenceExtract(sent)) {
        DTUPhrase dtu = new DTUPhrase(sent, bitset.clone(), new CoverageSet());
        if (dtu.consistencize()) {
          if (!seen.contains(dtu)) {
            matchQueue.offerLast(dtu);
            seen.add(dtu);
          }
        }
      }
      if (naacl2010) {
        growDTUs(matchQueue, growSource, false, false);
      } else {
        growDTUs(matchQueue, growSource, looseDTU, looseOutsideDTU);
      }
    }

    // Add each discontinuous phrase to grid:
    for (DTUPhrase dtu : seen) {
      //assert(dtu instanceof DTUPhrase);
      if (!noTargetGaps || dtu.eContiguous()) {
        if (!noUnalignedGaps || !dtu.hasUnalignedGap()) {
          if (!noUnalignedOrLooseGaps || !dtu.hasUnalignedOrLooseGap()) {
            if (!noUnalignedSubphrase || !dtu.hasUnalignedSubphrase()) {
              addPhraseToIndex(sent, dtu, true);
            }
          }
        }
      }
    }

    // Rules are processed after being enumerated:
    extractPhrasesFromGrid(sent);
  }

}
