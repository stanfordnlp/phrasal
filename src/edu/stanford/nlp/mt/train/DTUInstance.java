package edu.stanford.nlp.mt.train;

import java.util.*;


/**
 * @author Michel Galley
 */
public class DTUInstance extends AlignmentTemplateInstance {

  protected BitSet fSet=null, eSet=null;
  protected boolean discontinuous=false;

  DTUInstance() { super(); }

  /**
   * Construct alignment template from phrase pair set using bit sets fs and es.
   */
  public DTUInstance(WordAlignment sent, BitSet fs, BitSet es, boolean fContiguous, boolean eContiguous) {
    init(sent, fs, es, fContiguous, eContiguous);
  }

  public void init(WordAlignment sent, BitSet fs, BitSet es, boolean fContiguous, boolean eContiguous) {
    
    Map<Integer,Integer> fAlign = new HashMap<Integer,Integer>();
    Map<Integer,Integer> eAlign = new HashMap<Integer,Integer>();

    reset();
    this.sent = sent;
    int f1 = fs.nextSetBit(0), f2 = fs.length()-1;
    int e1 = es.nextSetBit(0), e2 = es.length()-1;
    this.fStartPos = f1;
    this.eStartPos = e1;
    // Init phrases:
    f = fContiguous ? sent.f().subsequence(f1,f2+1) : DiscontinuousSubSequences.subsequence(sent.f(), fs, fAlign);
    e = eContiguous ? sent.e().subsequence(e1,e2+1) : DiscontinuousSubSequences.subsequence(sent.e(), es, eAlign);
    fSet = fs;
    eSet = es;
    if (!fContiguous || !eContiguous)
      discontinuous = true;

    // Init alignments:
    if (!lazy)
      allocAlignmentArrays();
    for (int fi=f1; fi<=f2; ++fi) {
      if (!fs.get(fi))
        continue;
      //System.err.printf("f=%s %d-%d %d\n", fs, f1, f2, fi);
      int fIndex = fContiguous ? fi-f1 : fAlign.get(fi);
      assert(fIndex >= 0 && fIndex < Byte.MAX_VALUE && fIndex < f.size());
      for (Integer ei : sent.f2e(fi)) {
        if (!es.get(ei))
          continue;
        //System.err.printf("ei=%d e1=%d\n",ei,e1);
        int eIndex = eContiguous ? ei-e1 : eAlign.get(ei);
        if (eIndex < 0) continue;
        if (eIndex >= Byte.MAX_VALUE) continue;
        if (eIndex >= e.size()) continue;
        alTable.add(alignmentToNumber((byte)eIndex,(byte)fIndex));
        if (!lazy) {
          f2e[fIndex].add(eIndex);
          e2f[eIndex].add(fIndex);
        }
      }
    }
    align = new int[alTable.size()];
    int i=-1;
    for (Short a : alTable)
      align[++i] = a;
    if (DEBUG) {
      System.err.printf("New alignment template [%d-%d] [%d-%d]: %s\n",f1,f2,e1,e2,toString(true));
      System.err.println("String representation: "+Arrays.toString(align));
    }
    if(fContiguous) assert(fEndPos() == f2);
    if(eContiguous) assert(eEndPos() == e2);
  }

  @Override
  public boolean isDiscontinuous() {
    return true; 
  }

  public BitSet getFAlignment() { return fSet; }
  public BitSet getEAlignment() { return eSet; }
}
