package mt.train;

import java.util.*;


/**
 * An alignment template and its position within a sentence pair.
 *
 * @author Michel Galley
 */
public class AlignmentTemplateInstance extends AlignmentTemplate {

  private int fStartPos, eStartPos;

  private WordAlignment sent; // sentence pair from which altemp was extracted

  private float weight;

  public AlignmentTemplateInstance() { fStartPos = eStartPos = -1;  }

  public float getWeight() { return weight; }

  /**
   * Construct alignment template from phrase pair spanning f1-f2 and e1-e2.
   * @param lazy If true, some alignment member variables are null, 
   * which cause f2e() and e2f() to raise a NullPointerException.
   */
  public AlignmentTemplateInstance(WordAlignment sent, int f1, int f2, int e1, int e2, float weight) {
    init(sent, f1, f2, e1, e2, weight);
  }

  public void init(WordAlignment sent, int f1, int f2, int e1, int e2, float weight) {
    final boolean lazy = true;
    reset();
    this.sent = sent;
    this.fStartPos = f1;
    this.eStartPos = e1;
    this.weight = weight;
    // Init phrases:
    f = sent.f().subsequence(f1,f2+1);
    e = sent.e().subsequence(e1,e2+1);
    // Init alignments:
    if(!lazy)
      allocAlignmentArrays();
    for(int fi=f1; fi<=f2; ++fi) {
      int fIndex = fi-f1;
      assert(fIndex >= 0 && fIndex < Byte.MAX_VALUE && fIndex < f.size());
      for(Integer ei : sent.f2e(fi)) {
        //System.err.printf("ei=%d e1=%d\n",ei,e1);
        int eIndex = ei-e1;
        if(eIndex < 0) continue;
        if(eIndex >= Byte.MAX_VALUE) continue;
        if(eIndex >= e.size()) continue;
        alTable.add(alignmentToNumber((byte)eIndex,(byte)fIndex));
        if(!lazy) {
          f2e[fIndex].add(eIndex);
          e2f[eIndex].add(fIndex);
        }
      }
    }
    align = new int[alTable.size()]; // TODO fit two alignments in each int instead of 1
    int i=-1;
    for(Short a : alTable)
      align[++i] = a;
    if(DEBUG) {
      System.err.printf("New alignment template [%d-%d] [%d-%d]: %s\n",f1,f2,e1,e2,toString(true));
      System.err.println("String representation: "+Arrays.toString(align));
    }
    assert(fEndPos() == f2);
    assert(eEndPos() == e2);
  }

  public WordAlignment getSentencePair() { return sent; }

  public int fStartPos() { return fStartPos; }
  public int eStartPos() { return eStartPos; }

  public int fEndPos() { return fStartPos+f.size()-1; }
  public int eEndPos() { return eStartPos+e.size()-1; }

  //public boolean isConsistent() { return consistent; }
}
