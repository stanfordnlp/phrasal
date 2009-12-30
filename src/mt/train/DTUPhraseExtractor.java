package mt.train;

import mt.base.IString;
import mt.base.Sequence;
import mt.base.TrieIntegerArrayIndex;
import mt.tools.Levenshtein;

import java.util.*;
import java.io.IOException;

import edu.stanford.nlp.util.Triple;

/**
 * @author Michel Galley
 */
public class DTUPhraseExtractor extends AbstractPhraseExtractor {

  static public final String WITH_GAPS_OPT  = "withGaps";
  static public final String NO_TARGET_GAPS_OPT  = "noTargetGaps";
  static public final String ONLY_CROSS_SERIAL_OPT  = "onlyCrossSerialDTU";
  static public final String SKIP_UNALIGNED_GAPS_OPT  = "skipUnalignedGaps";
  static public final String ALL_SUBSEQUENCES_OPT  = "allSubsequences";
  static public final String ALL_SUBSEQUENCES_LOOSE_OPT  = "allSubsequencesLoose";

  // Only affects phrases with gaps:
  static public final String MAX_SPAN_OPT = "maxDTUSpan";
  static public final String MAX_SPAN_E_OPT = "maxDTUSpanE";
  static public final String MAX_SPAN_F_OPT = "maxDTUSpanF";

  static public final String MAX_SIZE_OPT = "maxDTUSize";
  static public final String MAX_SIZE_E_OPT = "maxDTUSizeE";
  static public final String MAX_SIZE_F_OPT = "maxDTUSizeF";

  static boolean withGaps, onlyCrossSerialDTU, noTargetGaps, skipUnalignedGaps, allSubsequences, allSubsequencesLoose;
  static int maxSizeE = Integer.MAX_VALUE, maxSizeF = Integer.MAX_VALUE, maxCSize = Integer.MAX_VALUE;
  static int maxSpanE = Integer.MAX_VALUE, maxSpanF = Integer.MAX_VALUE;

  public static final IString GAP_STR = new IString("X"); // uppercase, so shouldn't clash with other symbols

  enum CrossSerialType { NONE, TYPE1_E, TYPE1_F, TYPE2_E, TYPE2_F }
  static final BitSet noCrossSerial = new BitSet();

  private static final boolean DEBUG = System.getProperty("DebugDTU") != null;
  private static final int QUEUE_SZ = 1024;

  boolean printCrossSerialDTU = true;

  int fSentenceLength, eSentenceLength;

  LinearTimePhraseExtractor substringExtractor;

  private Deque<DTUPhrase> queue = new LinkedList<DTUPhrase>();
  private Set<DTUPhrase> seen = new HashSet<DTUPhrase>(QUEUE_SZ);

  public Object clone() throws CloneNotSupportedException {
    DTUPhraseExtractor c = (DTUPhraseExtractor) super.clone();
    c.substringExtractor = (LinearTimePhraseExtractor) substringExtractor.clone();
    c.substringExtractor.alGrid = c.alGrid;
    c.queue = new LinkedList<DTUPhrase>();
    c.seen = new HashSet<DTUPhrase>(QUEUE_SZ);
    c.dp = new Levenshtein<IString>();
    c.bitsets = new HashSet<BitSet>();
    return c;
  }

  public DTUPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) throws IOException {
    super(prop, alTemps, extractors);
    substringExtractor = new LinearTimePhraseExtractor(prop, alTemps, extractors);
    substringExtractor.alGrid = alGrid;
    System.err.println("Using DTU phrase extractor.");
  }

  static public void setDTUExtractionProperties(Properties prop) {

    // With gaps or not:
    String optStr = prop.getProperty(WITH_GAPS_OPT);
    withGaps =  optStr != null && !optStr.equals("false");

    // No gaps on the target:
    optStr = prop.getProperty(NO_TARGET_GAPS_OPT);
    noTargetGaps =  optStr != null && !optStr.equals("false");

    // Ignore DTU if a gap only covers unaligned words:
    optStr = prop.getProperty(SKIP_UNALIGNED_GAPS_OPT);
    skipUnalignedGaps =  optStr != null && !optStr.equals("false");

    // All subsequences:
    optStr = prop.getProperty(ALL_SUBSEQUENCES_OPT);
    allSubsequences = optStr != null && !optStr.equals("false");

    optStr = prop.getProperty(ALL_SUBSEQUENCES_LOOSE_OPT);
    allSubsequencesLoose = optStr != null && !optStr.equals("false");
    assert(!allSubsequences || !allSubsequencesLoose);

    // Only extracting cross-serial dependencies (for debugging):
    optStr = prop.getProperty(ONLY_CROSS_SERIAL_OPT);
    onlyCrossSerialDTU = optStr != null && !optStr.equals("false");

    String s;
    if((s = prop.getProperty(MAX_SPAN_F_OPT)) != null) {
      maxSpanF = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SPAN_OPT)) != null) {
      maxSpanF = Integer.parseInt(s);
    }

    if((s = prop.getProperty(MAX_SPAN_E_OPT)) != null) {
      maxSpanE = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SPAN_OPT)) != null) {
      maxSpanE = Integer.parseInt(s);
    }

   if((s = prop.getProperty(MAX_SIZE_F_OPT)) != null) {
      maxSizeF = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SIZE_OPT)) != null) {
      maxSizeF = Integer.parseInt(s);
    }

    if((s = prop.getProperty(MAX_SIZE_E_OPT)) != null) {
      maxSizeE = Integer.parseInt(s);
    } else if((s = prop.getProperty(MAX_SIZE_OPT)) != null) {
      maxSizeE = Integer.parseInt(s);
    }

    if(DEBUG)
      AlignmentTemplateInstance.lazy = false;
  }

  public static Set<Integer> bitSetToIntSet(BitSet b) {
    Set<Integer> s = new TreeSet<Integer>();
    int i = b.nextSetBit(0);
    if (i != -1) {
      s.add(i);
      for (i = b.nextSetBit(i+1); i >= 0; i = b.nextSetBit(i+1)) {
        int endOfRun = b.nextClearBit(i);
        do { s.add(i); }
        while (++i < endOfRun);
      }
    }
    return s;
  }

  static String stringWithGaps(Sequence<IString> f, BitSet b) {
    StringBuilder sb = new StringBuilder();
    Set<Integer> sel = bitSetToIntSet(b);
    int oldI = -1;
    for(int i : sel) {
      if(oldI != -1) {
        if(i > oldI+1) {
          sb.append(" ").append(GAP_STR).append(" ");
        } else {
          sb.append(" ");
        }
      }
      sb.append(f.get(i));
      oldI = i;
    }
    return sb.toString();
  }

  class DTUPhrase {

    WordAlignment sent;

    BitSet consistencizedF, consistencizedE;

    final BitSet f, e;

    DTUPhrase(DTUPhrase dtu) {
      sent = dtu.sent;
      f = (BitSet) dtu.f.clone();
      e = (BitSet) dtu.e.clone();
      consistencizedF = (BitSet) dtu.consistencizedF.clone();
      consistencizedE = (BitSet) dtu.consistencizedE.clone();
    }

    DTUPhrase(WordAlignment sent, int fi, int ei) {
      super();
      this.sent = sent;
      f = new BitSet();
      e = new BitSet();
      f.set(fi);
      e.set(ei);
      consistencizedF = new BitSet();
      consistencizedE = new BitSet();
    }

    DTUPhrase(WordAlignment sent, BitSet f, BitSet e) {
      super();
      this.sent = sent;
      this.f = f;
      this.e = e;
      consistencizedF = new BitSet();
      consistencizedE = new BitSet();
    }

    public boolean isConsistencizedE(int ei) { return consistencizedE.get(ei); }
    public boolean isConsistencizedF(int fi) { return consistencizedF.get(fi); }

    public void setConsistencizedE(int ei) { consistencizedE.set(ei); }
    public void setConsistencizedF(int fi) { consistencizedF.set(fi); }

    public boolean consistencize(int i, boolean source) {
      if(!consistencize(i, source, true))
        return false;

      //Drop phrase if too large:
      if(sizeF() > maxSizeF) return false;
      if(sizeE() > maxSizeE) return false;
      if(!fContiguous() || !eContiguous()) {
        if(sizeF() > maxSizeF) return false;
        if(sizeE() > maxSizeE) return false;
        if(spanF() > maxSpanF) return false;
        if(spanE() > maxSpanE) return false;
      }

      // Drop phrase if any of the gaps only contains unaligned words:
      return !(skipUnalignedGaps && hasUnalignedGap());
    }

    private boolean consistencize(int i, boolean source, boolean topLevel) {
      //System.err.println("C: "+toString());

      if(sizeF() > maxSizeF) return false;
      if(sizeE() > maxSizeE) return false;

      if(source) {
        setConsistencizedF(i);
        for(int ei : sent.f2e(i)) {
          if(!isConsistencizedE(ei)) {
            addE(ei);
            if(!consistencize(ei, false, false))
              return false;
          }
        }
      } else {
        setConsistencizedE(i);
        for(int fi : sent.e2f(i)) {
          if(!isConsistencizedF(fi)) {
            addF(fi);
            if(!consistencize(fi, true, false))
              return false;
          }
        }
      }

      // Consistencize words that have been added, but that may not be consistent:
      if(topLevel) {
        for(int fi : toConsistencizeInF()) {
          if(!consistencize(fi, true, true))
            return false;
        }
        for(int ei : toConsistencizeInE()) {
          if(!consistencize(ei, false, true))
            return false;
        }
      }
      return true;
    }

    BitSet e() { return e; }
    BitSet f() { return f; }

    public boolean hasUnalignedGap() {
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

    private boolean hasUnalignedGap(WordAlignment sent, BitSet fs, boolean source) {
      int startIdx, endIdx = 0;
      for(;;) {
        startIdx = fs.nextClearBit(fs.nextSetBit(endIdx));
        endIdx = fs.nextSetBit(startIdx)-1;
        if(startIdx > endIdx)
          break;
        boolean unalignedGap = true;
        for (int i=startIdx; i <= endIdx; ++i) {
          assert(!fs.get(i));
          if(source) {
            if(sent.f2e(i).size() > 0) {
              unalignedGap = false;
              break;
            }
          }  else {
            if(sent.e2f(i).size() > 0) {
              unalignedGap = false;
              break;
            }
          }
        }
        //System.err.printf("[%d-%d] %s\n", startIdx, endIdx, unalignedGap);
        if(unalignedGap)
         return true;
      }
      return false;
    }

    public boolean fContiguous() { return isContiguous(f); }
    public boolean eContiguous() { return isContiguous(e); }

    boolean isContiguous(BitSet bitset) {
      int i = bitset.nextSetBit(0);
      int j = bitset.nextClearBit(i+1);
      return (bitset.nextSetBit(j+1) == -1);
    }

    public BitSet getCrossSerialTypes() {
      assert(sane());
      BitSet b = new BitSet();
      if (isCrossSerialType1F()) b.set(CrossSerialType.TYPE1_F.ordinal());
      if (isCrossSerialType1E()) b.set(CrossSerialType.TYPE1_E.ordinal());
      if (isCrossSerialType2F()) b.set(CrossSerialType.TYPE2_F.ordinal());
      if (isCrossSerialType2E()) b.set(CrossSerialType.TYPE2_E.ordinal());
      return b;
    }

    private boolean isCrossSerialType2F() {
      // Detect type 2:
      // f: b1 a2 b3
      // e: a1 b2 a3
      for(int fi : bitSetToIntSet(f)) { // fi == a2
        int firstE=Integer.MAX_VALUE, lastE=Integer.MIN_VALUE;
        for(int ei : sent.f2e(fi)) {
          if(e.get(ei)) {
            if(ei < firstE) firstE = ei; // ei == a1
            if(ei > lastE) lastE = ei; // ei == a3
          }
        }
        if(firstE + 1 < lastE)  {
          for(int ei = firstE+1; ei < lastE; ++ei) { // ei == b1
            int firstF=Integer.MAX_VALUE, lastF=Integer.MIN_VALUE;
            for(int fi2 : sent.e2f(ei)) {
              if(fi2 < firstF) firstF = fi2; // fi2 == b1
              if(fi2 > lastF) lastF = fi2; // fi2 == b3
            }
            if(firstF < fi && fi < lastF) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private boolean isCrossSerialType2E() {
      for(int ei : bitSetToIntSet(e)) {
        int firstF=Integer.MAX_VALUE, lastF=Integer.MIN_VALUE;
        for(int fi : sent.e2f(ei)) {
          if(f.get(fi)) {
            if(fi < firstF) firstF = fi;
            if(fi > lastF) lastF = fi;
          }
        }
        if(firstF + 1 < lastF) {
          for(int fi = firstF+1; fi < lastF; ++fi) {
            int firstE=Integer.MAX_VALUE, lastE=Integer.MIN_VALUE;
            for(int ei2 : sent.f2e(fi)) {
              if(ei2 < firstE) firstE = ei2;
              if(ei2 > lastE) lastE = ei2;
            }
            if(firstE < ei && ei < lastE) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private boolean isCrossSerialType1F() {
      // Detect type 1:
      // f:   a1   b2
      // e:  a2 b1 a3 b3
      for(int fi : bitSetToIntSet(f)) { // fi == a1
        int firstE=Integer.MAX_VALUE, lastE=Integer.MIN_VALUE;
        for(int ei : sent.f2e(fi)) {
          if(ei < firstE) firstE = ei; // ei == a2
          if(ei > lastE) lastE = ei; // ei == a3
        }
        for(int ei = firstE; ei <= lastE; ++ei) { // ei == b1
          for(int fi2 : sent.e2f(ei)) {
            if(fi != fi2) { // fi2 == b2
              for(int ei2 : sent.f2e(fi2)) {
                if(ei2 < firstE || ei2 > lastE) {
                  return true;
                }
              }
            }
          }
        }
      }
      return false;
    }

    private boolean isCrossSerialType1E() {
      // Detect case 1:
      // e:   a1   b2
      // f:  a2 b1 a3 b3
      for(int ei : bitSetToIntSet(e)) { // ei == a1
        int firstF=Integer.MAX_VALUE, lastF=Integer.MIN_VALUE;
        for(int fi : sent.e2f(ei)) {
          if(fi < firstF) firstF = fi; // fi == a2
          if(fi > lastF) lastF = fi; // fi == a3
        }
        for(int fi = firstF; fi <= lastF; ++fi) { // fi == b1
          for(int ei2 : sent.f2e(fi)) {
            if(ei != ei2) { // ei2 == b2
              for(int fi2 : sent.e2f(ei2)) {
                if(fi2 < firstF || fi2 > firstF) {
                  return true;
                }
              }
            }
          }
        }
      }
      return false;
    }

    public boolean sane() { return f.equals(consistencizedF) && e.equals(consistencizedE); }

    public int sizeE() { return e.cardinality(); }
    public int sizeF() { return f.cardinality(); }

    public int spanE() { return e.length()-e.nextSetBit(0); }
    public int spanF() { return f.length()-f.nextSetBit(0); }

    public void addE(int ei) { e.set(ei); }
    public void addF(int fi) { f.set(fi); }

    public Set<Integer> toConsistencizeInF() {
      BitSet newF = (BitSet) f.clone();
      newF.xor(consistencizedF);
      //System.err.printf("x: %s %s %s %s\n", f, consistencizedF, newF, bitSetToIntSet(newF));
      return bitSetToIntSet(newF);
    }

    public Set<Integer> toConsistencizeInE() {
      BitSet newE = (BitSet) e.clone();
      newE.xor(consistencizedE);
      //System.err.printf("x: %s %s %s %s\n", e, consistencizedE, newE, bitSetToIntSet(newE));
      return bitSetToIntSet(newE);
    }

    public boolean containsE(int ei) { return e.get(ei); }
    public boolean containsF(int fi) { return f.get(fi); }

    public int getFirstE() { return e.nextSetBit(0); }
    public int getLastE() { return e.length()-1; }
    public int getFirstF() { return f.nextSetBit(0); }
    public int getLastF() { return f.length()-1; }

    @Override
    public String toString() {
      if (!sane()) {
        System.err.println("f: "+f);
        System.err.println("cf: "+consistencizedF);
        System.err.println("e: "+e);
        System.err.println("ce: "+consistencizedE);
      }
      String fp = stringWithGaps(sent.f(), f);
      String ep = stringWithGaps(sent.e(), e);
      return String.format("f={{{%s}}} e={{{%s}}}", fp, ep);
    }

    public String getCrossings() {
      assert(sane());
      BitSet types = getCrossSerialTypes();
      String fp = stringWithGaps(sent.f(), f);
      String ep = stringWithGaps(sent.e(), e);
      StringBuilder str = new StringBuilder();
      for(CrossSerialType t : CrossSerialType.values()) {
        if(types.get(t.ordinal())) {
          str.append(t).append(": ").append
            (String.format("f={{{%s}}} e={{{%s}}} || f={{{%s}}} e={{{%s}}}", fp, ep,
               sent.f().subsequence(f.nextSetBit(0),f.length()),
               sent.e().subsequence(e.nextSetBit(0),e.length())
            )).append("\n");
        }
      }
      return str.toString();
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

    // Current phrase is discontiguous, add discontiguous (and possibly contiguous) successors:
    private boolean addSuccessors(Collection<DTUPhrase> list) {
      int fsize = sent.f().size();
      int esize = sent.e().size();
      assert(sizeF() <= maxSizeF);
      assert(sizeE() <= maxSizeE);
      boolean growF = sizeF() < maxSizeF;
      boolean growE = sizeE() < maxSizeE;
      if (growF) {
        assert(spanF() <= maxSpanF);
        boolean growOutside = spanF() < maxSpanF;
        for(int s : successorsIdx(growOutside, f, fsize))
          list.add(new DTUPhrase(this).expandF(s));
      }
      if (growE) {
        assert(spanE() <= maxSpanE);
        boolean growOutside = spanE() < maxSpanE;
        for(int s : successorsIdx(growOutside, e, esize))
          list.add(new DTUPhrase(this).expandE(s));
      }
      return growF && growE;
    }

    public Collection<DTUPhrase> successors() {
      List<DTUPhrase> list = new LinkedList<DTUPhrase>();
      if (!noTargetGaps || isContiguous(e))
        addSuccessors(list);
      DTUPhrase ctu = toContiguous();
      if (ctu != null)
        list.add(ctu);
      return list;
    }

    public DTUPhrase toContiguous() {
      DTUPhrase p = new DTUPhrase(this);
      for(;;) {
        if (p.sizeF() > maxSizeF) return null;
        if (p.sizeE() > maxSizeE) return null;
        boolean cF = p.isContiguous(p.f);
        boolean cE = p.isContiguous(p.e);
        if(cF && cE)
          break;
        if (!cF) {
          int f1 = p.getFirstF(), f2 = p.getLastF();
          p.f.set(f1, f2+1);
          for(int fi = f1+1; fi < f2; ++fi)
            if(!f.get(fi))
              p.consistencize(fi, true);
        }
        if (!cE) {
          int e1 = p.getFirstE(), e2 = p.getLastE();
          p.e.set(e1, e2+1);
          for(int ei = e1+1; ei < e2; ++ei)
            if(!e.get(ei))
              p.consistencize(ei, false);
        }
      }
      if (p.sizeF() > maxSizeF) return null;
      if (p.sizeE() > maxSizeE) return null;
      return p;
    }

    private DTUPhrase expandF(int fi) { f.set(fi); return consistencize(fi, true) ? this : null; }
    private DTUPhrase expandE(int ei) { e.set(ei); return consistencize(ei, false) ? this : null; }

    boolean consistencize() {
      boolean done = false;
      while(!done) {
        //System.err.println("f1: "+f);
        //System.err.println("f2: "+consistencizedF);
        //System.err.println("e1: "+e);
        //System.err.println("e2: "+consistencizedE);
        boolean doneF=false, doneE=false;
        if (f.equals(consistencizedF)) {
          doneF = true;
        } else {
          for (int fi : bitSetToIntSet(f))
            if (!consistencizedF.get(fi))
              if (!consistencize(fi,true))
                return false;
          if (e.isEmpty())
            return false;
        }
        if (e.equals(consistencizedE)) {
          doneE = true;
        } else {
          for(int ei : bitSetToIntSet(e))
            if(!consistencizedE.get(ei))
              if(!consistencize(ei,false))
                return false;
          if(f.isEmpty())
            return false;
        }
        done = doneF && doneE;
      }
      return true;
    }

    private Deque<Integer> successorsIdx(boolean growOutside, BitSet bitset, int size) {
      Deque<Integer> ints = new LinkedList<Integer>();
      int si = 0;
      for (;;) {
        si = bitset.nextSetBit(si);
        if (si > 0) {
          if(ints.isEmpty() || ints.getLast() < si-1) {
            ints.add(si-1);
          }
        }
        if (si == -1) break;
        int ei = bitset.nextClearBit(++si);
        if (ei < size) {
          ints.add(ei);
        }
        si = ei;
      }

      if (!growOutside) {
        if (!ints.isEmpty() && ints.getFirst() < bitset.nextSetBit(0))
          ints.removeFirst();
        if (!ints.isEmpty() && ints.getLast() > bitset.length()-1)
          ints.removeLast();
        //System.err.printf("succ-idx: %s -> %s size=%d\n", bitset.toString(), ints.toString(), size);
      }
      return ints;
    }
  }

  @Override
  boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    return false;
  }

  Levenshtein<IString> dp = new Levenshtein<IString>();
  Set<BitSet> bitsets = new HashSet<BitSet>();

  // Extract all subsequences:
  Set<BitSet> subsequenceExtract(WordAlignment sent) {

    TrieIntegerArrayIndex fTrieIndex = alTemps.getSourceFilter().getSourceTrie();
    assert((fTrieIndex != null) == (allSubsequences || allSubsequencesLoose));
    
    Deque<Triple<Integer,Integer,BitSet>> q = new LinkedList<Triple<Integer,Integer,BitSet>>();
    bitsets.clear();
    for (int i=0; i < sent.f().size(); ++i) {
      //System.err.println("i: "+i);
      q.clear();
      int startState = TrieIntegerArrayIndex.IDX_ROOT;
      q.offerFirst(new Triple<Integer,Integer,BitSet>(i, startState, new BitSet()));
      while (!q.isEmpty()) {
        Triple<Integer,Integer,BitSet> el = q.pollLast();
        int pos = el.first();
        int curState = el.second();
        BitSet bitset = el.third();
        if (!bitset.isEmpty()) {
          bitsets.add(bitset);
          //System.err.printf("bs=%s %s\n",bitset,stringWithGaps(sent.f(),bitset));
        }
        if (pos >= sent.f().size()) continue;
        // Try to match a terminal:
        int nextState = fTrieIndex.getSuccessor(curState, sent.f().get(pos).id);
        if (nextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
          BitSet newBitset = (BitSet) bitset.clone();
          newBitset.set(pos);
          q.offerFirst(new Triple<Integer,Integer,BitSet>(pos+1,nextState,newBitset));
        }
        // Try to match non terminals:
        int ntNextState = fTrieIndex.getSuccessor(curState, DTUPhraseExtractor.GAP_STR.id);
        if (ntNextState != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
          int lastPos = i+maxSpanF;
          for (int pos2=pos+1; pos2<=lastPos; ++pos2) {
            if (pos2 >= sent.f().size()) break;
            int next2State = fTrieIndex.getSuccessor(ntNextState, sent.f().get(pos2).id);
            if (next2State != TrieIntegerArrayIndex.IDX_NOSUCCESSOR) {
              BitSet newBitset = (BitSet) bitset.clone();
              newBitset.set(pos2);
              q.offerFirst(new Triple<Integer,Integer,BitSet>(pos2+1,next2State,newBitset));
            }
          }
        }
      }
    }
    return bitsets;
  }

  @Override
  public void extractPhrases(WordAlignment sent) {

    substringExtractor.extractPhrases(sent);

    if (DEBUG) {
      System.err.println("f: "+sent.f());
      System.err.println("e: "+sent.e());
      System.err.println("a: "+sent.toString());
    }

    int fsize = sent.f().size();
    int esize = sent.e().size();
    if (needAlGrid) {
      alGrid.init(esize,fsize);
      if(fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
        alGrid.printAlTempInGrid("line: "+sent.getId(),sent,null,System.err);
    }
    
    queue.clear();
    seen.clear();

    // Add minimal phrases:
    for (int ei=0; ei<sent.e().size(); ++ei) {
      for (int fi : sent.e2f(ei)) {
        DTUPhrase p = new DTUPhrase(sent, fi, ei);
        if (p.consistencize(fi, true)) {
          if (!seen.contains(p)) {
            //if(DEBUG) System.err.println("dtu(m): "+p.toString());
            if(onlyCrossSerialDTU && p.getCrossSerialTypes().isEmpty()) 
              continue;
            queue.offerLast(p);
            seen.add(p);
            assert(p.sane());
          }
        }
      }
    }

    // Add all subsequence phrases:
    if (allSubsequencesLoose) {
      for (BitSet bitset : subsequenceExtract(sent)) {
        DTUPhrase dtu = new DTUPhrase(sent, (BitSet)bitset.clone(), new BitSet());
        if (dtu.consistencize()) {
          //System.err.println("sent: dtu: "+dtu);
          if (!noTargetGaps || dtu.eContiguous()) {
            if (!seen.contains(dtu)) {
              queue.offerLast(dtu);
              //extractPhrase(sent, dtu.f(), dtu.e(), dtu.fContiguous(), dtu.eContiguous(), true);
              seen.add(dtu);
            }
          }
        }
      }
    }

    // Expand rules:
    while (!queue.isEmpty()) {
      DTUPhrase dtu = queue.pollFirst();
      if (withGaps) {
        if (!noTargetGaps || dtu.eContiguous()) {
          if (!dtu.eContiguous() || !dtu.fContiguous()) {
            // Only extract non-contiguous phrases:
            AlignmentTemplate alTemp = extractPhrase(sent, dtu.f(), dtu.e(), dtu.fContiguous(), dtu.eContiguous(), true);
            if(DEBUG && alTemp != null)
              System.err.printf("dtu: %s\n%s", alTemp.toString(false), dtu.getCrossings());
          }
        }
      } else {
        extractPhrase(sent, dtu.getFirstF(), dtu.getLastF(), dtu.getFirstE(), dtu.getLastE(), true, 1.0f);
      }
      if (!onlyCrossSerialDTU) {
        for (DTUPhrase sp : dtu.successors()) {
          if (sp != null && !seen.contains(sp)) {
            queue.offerLast(sp);
            seen.add(sp);
          }
        }
      }
    }

    // Add rules specific to test set:
    if (allSubsequences) {
      for (BitSet bitset : subsequenceExtract(sent)) {
        DTUPhrase dtu = new DTUPhrase(sent, (BitSet)bitset.clone(), new BitSet());
        if (dtu.consistencize()) {
          //System.err.println("sent: dtu: "+dtu);
          if (!noTargetGaps || dtu.eContiguous()) {
            if (!seen.contains(dtu)) {
              extractPhrase(sent, dtu.f(), dtu.e(), dtu.fContiguous(), dtu.eContiguous(), true);
              seen.add(dtu);
            }
          }
        }
      }
    }
    if (needAlGrid)
      extractPhrasesFromAlGrid(sent);
  }
}
