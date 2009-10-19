package mt.train;

import mt.base.IString;
import mt.base.Sequence;

import java.util.*;


/**
 * @author Michel Galley
 */
public class DTUPhraseExtractor extends AbstractPhraseExtractor {

  private static final int QUEUE_SZ = 1024;

  final boolean withGaps;

  int fSentenceLength, eSentenceLength;

  private final PriorityQueue<Phrase> queue = new PriorityQueue<Phrase>(QUEUE_SZ, new PhraseComparator());
  private final Set<Phrase> seen = new HashSet<Phrase>(QUEUE_SZ);

  public DTUPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {
    super(prop, alTemps, extractors);
    String optStr = prop.getProperty(CombinedFeatureExtractor.WITH_GAPS_OPT);
    withGaps =  optStr != null && !optStr.equals("false");
    System.err.println("Using DTU phrase extractor.");
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
          sb.append(" [..] ");
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

    Collection<? extends Phrase> successors(int fsize, int esize);

    boolean consistencize(int i, boolean source);

    boolean sane();

    int getFirstE();
    int getLastE();
    int getFirstF();
    int getLastF();

    int sizeE();
    int sizeF();

    void addE(int ei);
    void addF(int fi);

    boolean containsE(int ei);
    boolean containsF(int fi);

    void setConsistencizedE(int ei);
    void setConsistencizedF(int fi);

    boolean isConsistencizedE(int ei);
    boolean isConsistencizedF(int fi);

    Set<Integer> toConsistencizeInE();
    Set<Integer> toConsistencizeInF();
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
      return true;
    }

    public boolean consistencize(int i, boolean source, boolean topLevel) {
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

    public boolean sane() { return true; }

    public int getFirstE() { return e1; }
    public int getLastE() { return e2; }
    public int getFirstF() { return f1; }
    public int getLastF() { return f2; }

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
    public Collection<? extends Phrase> successors(int fsize, int esize) {
      List<AbstractPhrase> list = new ArrayList<AbstractPhrase>(4);
      if(f2-f1 <= maxPhraseLenF) {
        if(f1 > 0) list.add(new CTUPhrase(this).expandLeftF());
        if(f2+1 < fsize) list.add(new CTUPhrase(this).expandRightF());
      }
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

    @Override
    public int sizeF() { return f2-f1+1; }

    @Override
    public int sizeE() { return e2-e1+1; }

    @Override
    public boolean containsE(int ei) { return (e1 <= ei && ei <= e2); }
    
    @Override
    public boolean containsF(int fi) { return (f1 <= fi && fi <= f2); }

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

    public boolean sane() { return f.equals(consistencizedF) && e.equals(consistencizedE); } 

    public int sizeE() { return e.cardinality(); }
    public int sizeF() { return f.cardinality(); }

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
      return String.format("f={{{%s}}} e={{{%s}}}", stringWithGaps(sent.f(), f), stringWithGaps(sent.e(), e));
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

    public Collection<? extends Phrase> successors(int fsize, int esize) {
      List<AbstractPhrase> list = new LinkedList<AbstractPhrase>();
      if(sizeF() < maxPhraseLenF) {
        for(int s : successorsIdx(f, fsize))
          list.add(new DTUPhrase(this).expandF(s));
      }
      if(sizeE() <= maxPhraseLenE) {
        for(int s : successorsIdx(e, esize))
          list.add(new DTUPhrase(this).expandE(s));
      }
      return list;
    }

    private DTUPhrase expandF(int fi) { f.set(fi); return consistencize(fi, true) ? this : null; }
    private DTUPhrase expandE(int ei) { e.set(ei); return consistencize(ei, false) ? this : null; }

    private Deque<Integer> successorsIdx(BitSet bitset, int size) {
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
      //System.err.printf("succ-idx: %s -> %s size=%d\n", bitset.toString(), ints.toString(), size);
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

    if(withGaps) {
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
            if(withGaps)
              System.err.println("dtu(m): "+p.toString());
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
        System.err.println("dtu: "+p.toString());
        assert(p.sane());
        // TODO
      } else {
        extractPhrase(sent, p.getFirstF(), p.getLastF(), p.getFirstE(), p.getLastE(), true, 1.0f);
      }
      for(Phrase sp : p.successors(sent.f().size(), sent.e().size())) {
        if(sp != null && !seen.contains(sp)) {
          queue.offer(sp);
          seen.add(sp);
        }
      }
    }
  }
}
