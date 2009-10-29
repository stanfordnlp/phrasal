package mt.train;

import mt.base.IString;
import mt.base.Sequence;

import java.util.*;

/**
 * @author Michel Galley
 */
public class DTUPhraseExtractor extends AbstractPhraseExtractor {

  static public final String WITH_GAPS_OPT  = "withGaps";
  static public final String NO_TARGET_GAPS_OPT  = "noTargetGaps";
  static public final String ONLY_CROSS_SERIAL_OPT  = "onlyCrossSerialDTU";

  // Only affects phrases with gaps:
  static public final String MAX_SPAN_OPT = "maxDTUSpan";
  static public final String MAX_SPAN_E_OPT = "maxDTUSpanE";
  static public final String MAX_SPAN_F_OPT = "maxDTUSpanF";

  static public final String MAX_SIZE_OPT = "maxDTUSize";
  static public final String MAX_SIZE_E_OPT = "maxDTUSizeE";
  static public final String MAX_SIZE_F_OPT = "maxDTUSizeF";

  static boolean withGaps, onlyCrossSerialDTU, noTargetGaps;
  static int maxSizeE = Integer.MAX_VALUE, maxSizeF = Integer.MAX_VALUE, maxCSize = Integer.MAX_VALUE;
  static int maxSpanE = Integer.MAX_VALUE, maxSpanF = Integer.MAX_VALUE;

  public static final IString GAP_STR = new IString("X"); // uppercase, so shouldn't clash with other symbols

  enum CrossSerialType { NONE, TYPE1_E, TYPE1_F, TYPE2_E, TYPE2_F }
  static final BitSet noCrossSerial = new BitSet();

  private static final boolean DEBUG = System.getProperty("DebugDTU") != null;
  private static final int QUEUE_SZ = 1024;

  boolean printCrossSerialDTU = true;

  int fSentenceLength, eSentenceLength;

  private final PriorityQueue<Phrase> queue = new PriorityQueue<Phrase>(QUEUE_SZ, new PhraseComparator());
  private final Set<Phrase> seen = new HashSet<Phrase>(QUEUE_SZ);

  public DTUPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    System.err.println("Using DTU phrase extractor.");
    System.err.println("DTUPhraseExtractor: "+maxPhraseLenF);
  }

  static public void setDTUExtractionProperties(Properties prop) {

    // With gaps or not:
    String optStr = prop.getProperty(WITH_GAPS_OPT);
    withGaps =  optStr != null && !optStr.equals("false");
    if(!withGaps) {
      System.err.println
       ("WARNING: using DTUPhraseExtractor without gaps! "+
        "Gapless phrases are more efficiently extracted using LinearTimePhraseExtractor");
    }

    // No gaps on the target:
    optStr = prop.getProperty(NO_TARGET_GAPS_OPT);
    noTargetGaps =  optStr != null && !optStr.equals("false");

    // With gaps or not:
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

  static Set<Integer> bitSetToIntSet(BitSet b) {
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

  interface Phrase {

    Collection<Phrase> successors();

    public Phrase toContiguous();

    boolean consistencize(int i, boolean source);

    boolean sane();

    int getFirstE();
    int getLastE();
    int getFirstF();
    int getLastF();

    int sizeE();
    int sizeF();

    int spanE();
    int spanF();

    void addE(int ei);
    void addF(int fi);

    boolean containsE(int ei);
    boolean containsF(int fi);

    boolean fContiguous();
    boolean eContiguous();

    void setConsistencizedE(int ei);
    void setConsistencizedF(int fi);

    boolean isConsistencizedE(int ei);
    boolean isConsistencizedF(int fi);

    Set<Integer> toConsistencizeInE();
    Set<Integer> toConsistencizeInF();

    BitSet getCrossSerialTypes();

    String getCrossings();
  }

  abstract class AbstractPhrase implements Phrase {

    WordAlignment sent;

    BitSet consistencizedF, consistencizedE;

    public boolean isConsistencizedE(int ei) { return consistencizedE.get(ei); }
    public boolean isConsistencizedF(int fi) { return consistencizedF.get(fi); }

    public void setConsistencizedE(int ei) { consistencizedE.set(ei); }
    public void setConsistencizedF(int fi) { consistencizedF.set(fi); }

    public boolean consistencize(int i, boolean source) {
      if(!consistencize(i, source, true))
        return false;
      sane();
      if(sizeF() > maxPhraseLenF) return false;
      if(sizeE() > maxPhraseLenE) return false;
      if(!fContiguous() || !eContiguous()) {
        if(sizeF() > maxSizeF) return false;
        if(sizeE() > maxSizeE) return false;
        if(spanF() > maxSpanF) return false;
        if(spanE() > maxSpanE) return false;
      }
      return true;
    }

    private boolean consistencize(int i, boolean source, boolean topLevel) {
      //System.err.println("C: "+toString());

      if(sizeF() > maxPhraseLenF) return false;
      if(sizeE() > maxPhraseLenE) return false;

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
  }

  class CTUPhrase extends AbstractPhrase {

    private int f1, f2, e1, e2;

    CTUPhrase(CTUPhrase ctu) {
      sent = ctu.sent;
      f1 = ctu.f1; f2 = ctu.f2;
      e1 = ctu.e1; e2 = ctu.e2;
      consistencizedF = (BitSet) ctu.consistencizedF.clone();
      consistencizedE = (BitSet) ctu.consistencizedE.clone();
    }

    CTUPhrase(WordAlignment sent, int fi, int ei) {
      super();
      this.sent = sent;
      f1 = f2 = fi;
      e1 = e2 = ei;
      consistencizedF = new BitSet();
      consistencizedE = new BitSet();
    }

    @Override
    public BitSet getCrossSerialTypes() { return noCrossSerial; }

    @Override
    public String getCrossings() { return ""; }

    public boolean sane() { return true; }

    public int getFirstE() { return e1; }
    public int getLastE() { return e2; }
    public int getFirstF() { return f1; }
    public int getLastF() { return f2; }

    @Override public boolean fContiguous() { return true; }
    @Override public boolean eContiguous() { return true; }

    @Override
    public Set<Integer> toConsistencizeInE() {
      Set<Integer> s = new TreeSet<Integer>();
      int pos = e1;
      for(;;) {
        pos = consistencizedE.nextClearBit(pos);
        if(pos < e1 || e2 < pos)
          break;
        s.add(pos);
        ++pos;
      }
      return s;
    }

    @Override
    public Set<Integer> toConsistencizeInF() {
      Set<Integer> s = new TreeSet<Integer>();
      int pos = f1;
      for(;;) {
        pos = consistencizedF.nextClearBit(pos);
        if(pos < f1 || f2 < pos)
          break;
        s.add(pos);
        ++pos;
      }
      return s;
    }

    @Override
    public Collection<Phrase> successors() {
      int fsize = sent.f().size();
      List<Phrase> list = new ArrayList<Phrase>(4);
      if(f2-f1 <= maxPhraseLenF) {
        if(f1 > 0) list.add(new CTUPhrase(this).expandLeftF());
        if(f2+1 < fsize) list.add(new CTUPhrase(this).expandRightF());
      }
      int esize = sent.e().size();
      if(e2-e1 <= maxPhraseLenE) {
        if(e1 > 0) list.add(new CTUPhrase(this).expandLeftE());
        if(e2+1 < esize) list.add(new CTUPhrase(this).expandRightE());
      }
      return list;
    }

    @Override
    public void addE(int ei) {
      if(ei < e1) {
        e1 = ei;
      } else if(ei > e2) {
        e2 = ei;
      }
    }

    @Override
    public void addF(int fi) {
      if(fi < f1) {
        f1 = fi;
      } else if(fi > f2) {
        f2 = fi;
      }
    }

    @Override public Phrase toContiguous() { return new CTUPhrase(this); }

    @Override public int sizeF() { return f2-f1+1; }
    @Override public int sizeE() { return e2-e1+1; }

    @Override public int spanF() { return sizeF(); }
    @Override public int spanE() { return sizeE(); }

    @Override public boolean containsE(int ei) { return (e1 <= ei && ei <= e2); }
    @Override public boolean containsF(int fi) { return (f1 <= fi && fi <= f2); }

    @Override
    public String toString() {
      return String.format("f=[%d %d] e=[%d %d] cf=%s ce=%s", f1, f2, e1, e2, consistencizedF.toString(), consistencizedE.toString());
    }

    private CTUPhrase expandLeftF()  { --f1; return consistencize(f1, true)  ? this : null; }
    private CTUPhrase expandRightF() { ++f2; return consistencize(f2, true)  ? this : null; }
    private CTUPhrase expandLeftE()  { --e1; return consistencize(e1, false) ? this : null; }
    private CTUPhrase expandRightE() { ++e2; return consistencize(e2, false) ? this : null; }

    @Override
    public int hashCode() {
      return Arrays.hashCode(new int[] {e1, e2, f1, f2});
    }

    @Override
    public boolean equals(Object o) {
      if(!(o instanceof CTUPhrase))
        return false;
      CTUPhrase ctu = (CTUPhrase) o;
      return e1 == ctu.e1 && e2 == ctu.e2 &&
             f1 == ctu.f1 && f2 == ctu.f2;
    }
  }

  class DTUPhrase extends AbstractPhrase {

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

    BitSet e() { return e; }
    BitSet f() { return f; }

    @Override public boolean fContiguous() { return isContiguous(f); }
    @Override public boolean eContiguous() { return isContiguous(e); }

    boolean isContiguous(BitSet bitset) {
      int i = bitset.nextSetBit(0);
      int j = bitset.nextClearBit(i+1);
      return (bitset.nextSetBit(j+1) == -1);
    }

    @Override
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
      assert(sane());
      String fp = stringWithGaps(sent.f(), f);
      String ep = stringWithGaps(sent.e(), e);
      return String.format("f={{{%s}}} e={{{%s}}}", fp, ep);
    }

    @Override
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
      if(!(o instanceof DTUPhrase))
        return false;
      DTUPhrase dtu = (DTUPhrase) o;
      return f.equals(dtu.f) && e.equals(dtu.e);
    }

    // Current phrase is contiguous, add contiguous successors:
    private boolean addContiguousSuccessors(Collection<Phrase> list) {
      int fsize = sent.f().size();
      int esize = sent.e().size();
      assert(sizeF() <= maxPhraseLenF);
      assert(sizeE() <= maxPhraseLenE);
      boolean growF = sizeF() < maxPhraseLenF;
      boolean growE = sizeE() < maxPhraseLenE;
      if(growF) {
        for(int s : successorsIdx(true, f, fsize))
          list.add(new DTUPhrase(this).expandF(s));
      }
      if(growE) {
        for(int s : successorsIdx(true, e, esize))
          list.add(new DTUPhrase(this).expandE(s));
      }
      return growF && growE;
    }

    // Current phrase is discontiguous, add discontiguous (and possibly contiguous) successors:
    private boolean addDiscontiguousSuccessors(Collection<Phrase> list) {
      int fsize = sent.f().size();
      int esize = sent.e().size();
      assert(sizeF() <= maxSizeF);
      assert(sizeE() <= maxSizeE);
      boolean growF = sizeF() < maxSizeF;
      boolean growE = sizeE() < maxSizeE;
      if(growF) {
        assert(spanF() <= maxSpanF);
        boolean growOutside = spanF() < maxSpanF;
        for(int s : successorsIdx(growOutside, f, fsize))
          list.add(new DTUPhrase(this).expandF(s));
      }
      if(growE) {
        assert(spanE() <= maxSpanE);
        boolean growOutside = spanE() < maxSpanE;
        for(int s : successorsIdx(growOutside, e, esize))
          list.add(new DTUPhrase(this).expandE(s));
      }
      return growF && growE;
    }

    public Collection<Phrase> successors() {
      List<Phrase> list = new LinkedList<Phrase>();
      if(isContiguous(f) && isContiguous(e)) {
        addContiguousSuccessors(list);
      } else {
        if(!noTargetGaps || isContiguous(e))
          addDiscontiguousSuccessors(list);
        Phrase ctu = toContiguous();
        if(ctu != null)
          list.add(ctu);
      }
      return list;
    }

    @Override
    public Phrase toContiguous() {
      DTUPhrase p = new DTUPhrase(this);
      for(;;) {
        if(p.sizeF() > maxPhraseLenF) return null;
        if(p.sizeE() > maxPhraseLenE) return null;
        boolean cF = p.isContiguous(p.f);
        boolean cE = p.isContiguous(p.e);
        if(cF && cE)
          break;
        if(!cF) {
          int f1 = p.getFirstF(), f2 = p.getLastF();
          p.f.set(f1, f2+1);
          for(int fi = f1+1; fi < f2; ++fi)
            if(!f.get(fi))
              p.consistencize(fi, true);
        }
        if(!cE) {
          int e1 = p.getFirstE(), e2 = p.getLastE();
          p.e.set(e1, e2+1);
          for(int ei = e1+1; ei < e2; ++ei)
            if(!e.get(ei))
              p.consistencize(ei, false);
        }
      }
      if(p.sizeF() > maxPhraseLenF) return null;
      if(p.sizeE() > maxPhraseLenE) return null;
      return p;
    }

    private DTUPhrase expandF(int fi) { f.set(fi); return consistencize(fi, true) ? this : null; }
    private DTUPhrase expandE(int ei) { e.set(ei); return consistencize(ei, false) ? this : null; }

    private Deque<Integer> successorsIdx(boolean growOutside, BitSet bitset, int size) {
      Deque<Integer> ints = new LinkedList<Integer>();
      int si = 0;
      for(;;) {
        si = bitset.nextSetBit(si);
        if(si > 0) {
          if(ints.isEmpty() || ints.getLast() < si-1) {
            ints.add(si-1);
          }
        }
        if(si == -1) break;
        int ei = bitset.nextClearBit(++si);
        if(ei < size) {
          ints.add(ei);
        }
        si = ei;
      }
      if(!growOutside) {
        if(!ints.isEmpty() && ints.getFirst() < bitset.nextSetBit(0))
          ints.removeFirst();
        if(!ints.isEmpty() && ints.getLast() > bitset.length()-1)
          ints.removeLast();
        //System.err.printf("succ-idx: %s -> %s size=%d\n", bitset.toString(), ints.toString(), size);
      }
      return ints;
    }
  }

  class PhraseComparator implements Comparator<Phrase> {

    public int compare(Phrase o1, Phrase o2) {
      int sz1 = o1.sizeE()+o1.sizeF();
      int sz2 = o2.sizeE()+o2.sizeF();
      return (Double.compare(sz1,sz2));
    }
  }

  @Override
  public void extractPhrases(WordAlignment sent) {

    if(DEBUG) {
      System.err.println("f: "+sent.f());
      System.err.println("e: "+sent.e());
      System.err.println("a: "+sent.toString());
    }

    queue.clear();
    seen.clear();

    // Add minimal phrases:
    for(int ei=0; ei<sent.e().size(); ++ei) {
      for(int fi : sent.e2f(ei)) {
        Phrase p = withGaps ? new DTUPhrase(sent, fi, ei) : new CTUPhrase(sent, fi, ei);
        if(p.consistencize(fi, true)) {
          if(!seen.contains(p)) {
            //if(DEBUG) System.err.println("dtu(m): "+p.toString());
            if(onlyCrossSerialDTU && p.getCrossSerialTypes().isEmpty()) 
              continue;
            queue.add(p);
            seen.add(p);
            assert(p.sane());
          }
        }
      }
    }

    // Expand rules:
    while(!queue.isEmpty()) {
      Phrase p = queue.poll();
      if(withGaps) {
        assert(p instanceof DTUPhrase);
        DTUPhrase dtu = (DTUPhrase) p;
        if(!noTargetGaps || dtu.eContiguous()) {
        AlignmentTemplate alTemp = extractPhrase(sent, dtu.f(), dtu.e(), dtu.fContiguous(), dtu.eContiguous(), true);
        if(DEBUG && alTemp != null)
          System.err.printf("dtu: %s\n%s", alTemp.toString(false), p.getCrossings());
        }
      } else {
        extractPhrase(sent, p.getFirstF(), p.getLastF(), p.getFirstE(), p.getLastE(), true, 1.0f);
      }
      if(!onlyCrossSerialDTU) {
        for(Phrase sp : p.successors()) {
          if(sp != null && !seen.contains(sp)) {
            queue.offer(sp);
            seen.add(sp);
          }
        }
      }
    }
  }
}
