package edu.stanford.nlp.mt.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.math.ArrayMath;

/** This class maintains and calculates the word alignments inside a phrase.
 *  The static factory method maintains a set of known alignment patterns and
 *  returns the same object for identical alignment patterns. Alignment
 *  patterns in String form can either be <code>PHRASE_ALIGNMENT</code> to indicate 
 *  that the whole phrase is aligned in a not further specified manner, or a sequence of
 *  whitespace-separated items, each of which is a possibly empty
 *  parenthesized list of integers, such as "()" or "(1,5,11)".
 *
 *  @author Dan Cer
 *  @author Michel Galley
 */
public class PhraseAlignment {

  // This is a fairly large object, though it is instantiated only once for each
  // alignment matrix.
  // Since phrase pairs are typically small, there are not that many distinct
  // matrices.
  // Hence, storing these alignments typically has the cost of just storing one
  // pointer in memory.

  public static final String PHRASE_ALIGNMENT = "I-I";
  
  final IString str;
  public final int[][] t2s;

  private PhraseAlignment(String s) {
    // System.err.println("align: "+s);
    String stringRep = s.intern();
    if (stringRep.equals(PHRASE_ALIGNMENT)) {
      // No internal alignment
      t2s = null;
    } else {
      String[] els = stringRep.split("\\s+");
      t2s = new int[els.length][];
      for (int i = 0; i < t2s.length; ++i) {
        // System.err.printf("(%d): %s\n",i,els[i]);
        if (!els[i].equals("()")) {
          String[] els2 = els[i].split(",");
          t2s[i] = new int[els2.length];
          for (int j = 0; j < t2s[i].length; ++j) {
            // System.err.printf("(%d): %s\n",j,els2[j]);
            String num = els2[j].replaceAll("[()]", "");
            t2s[i][j] = Integer.parseInt(num);
          }
        }
      }
    }
    str = new IString(stringRep);
    // System.err.println(Arrays.deepToString(e2f));
  }

  @Override
  public boolean equals(Object o) {
    assert (o instanceof PhraseAlignment);
    PhraseAlignment a = (PhraseAlignment) o;
    return this.str.id == a.str.id;
  }

  @Override
  public int hashCode() {
    return str.hashCode();
  }

  public int[] t2s(int i) {
    return (t2s != null) ? (i < t2s.length ? t2s[i] : null) : new int[] { i };
  }

  private static String toStr(int[][] alignmentGrid) {
    StringBuilder sb = new StringBuilder();
    for (int ei=0; ei<alignmentGrid.length; ++ei) {
      if (ei>0) sb.append(' ');
      sb.append('(');
      if (alignmentGrid[ei] != null) {
        int i=0;
        for (int fi : alignmentGrid[ei]) {
          if (i++ > 0) sb.append(',');
          sb.append(fi);
        }
      }
      sb.append(')');
    }
    return sb.toString();
  }

  public String t2sStr() {
    return toStr(t2s);
  }
  
  public String s2tStr() {
    return toStr(s2t());
  }

  private int[][] s2t() {
    if (t2s == null) return null;
    List<List<Integer>> f2eL = new LinkedList<List<Integer>>();
    for (int ei=0; ei<t2s.length; ++ei) {
      if (t2s[ei] != null) {
        for (int fi : t2s[ei]) {
          while (f2eL.size() <= fi)
            f2eL.add(new LinkedList<Integer>());
          f2eL.get(fi).add(ei);
        }
      }
    }
    int[][] s2t = new int[f2eL.size()][];
    for (int fi=0; fi<f2eL.size(); ++fi) {
      s2t[fi] = new int[f2eL.get(fi).size()];
      for (int ei=0; ei<f2eL.get(fi).size(); ++ei) {
        s2t[fi][ei] = f2eL.get(fi).get(ei);
      }
    }
    return s2t;
  }

  private static final Map<String, PhraseAlignment> map = new ConcurrentHashMap<String, PhraseAlignment>(1000);

  public static PhraseAlignment getPhraseAlignment(String string) {
    PhraseAlignment holder = map.get(string);
    if (holder == null) {
      holder = new PhraseAlignment(string);
      map.put(string, holder);
    }
    return holder;
  }

  @Override
  public String toString() {
    return str.toString();
  }

  public IString toIString() {
    return str;
  }

  public boolean hasAlignment() {
    return t2s != null;
  }

  public int size() {
    return (t2s != null) ? t2s.length : 0;
  }

  /**
   * Find average of source positions that correspond to the current tgtPos 
   * w.r.t to the given phrase alignment.
   * The input target position and the output source position are relative
   * positions within the current phrase.
   * 
   * @param tgtPos
   * @param alignment
   * @return
   */
  public int findSrcAvgPos(int tgtPos){
    if(t2s==null) { // no internal alignment
      assert(tgtPos==0);
      return 0;
    }
    
    int srcAvgPos = -1;
    int distance = 0;
    
    // System.err.println("findSrcAvgPos tgtPos=" + tgtPos + ", alignment=" + alignment);
    int[] alignments;
    while(true){
      // look right
      int rightPos = tgtPos + distance;
      boolean isStop = true;
      if(rightPos<t2s.length){
        alignments = (rightPos < t2s.length ? t2s[rightPos] : null); // t2s(rightPos);
        if (alignments != null) {
          // System.err.print("right " + rightPos + ": " + Util.intArrayToString(alignments));
          srcAvgPos = ArrayMath.mean(alignments);
          break;
        }
        
        isStop = false;
      }
      
      // look left
      int leftPos = tgtPos - distance;
      if(leftPos>=0 && leftPos!=rightPos){
        alignments = (leftPos < t2s.length ? t2s[leftPos] : null); // t2s(leftPos);
        if (alignments != null) {
          // System.err.print("left " + leftPos + ": " + Util.intArrayToString(alignments));
          srcAvgPos = ArrayMath.mean(alignments);
          break;
        }
        
        isStop = false;
      }
      
      distance++;
      if (isStop) break;
    }
    
    return srcAvgPos;
  }
}
