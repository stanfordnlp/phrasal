package edu.stanford.nlp.mt.base;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class PhraseAlignment {

  // This is a fairly large object, though it is instantiated only once for each alignment matrix.
  // Since phrase pairs are typically small, there are not that many distinct matrices.
  // Hence, storing these alignments typically has the cost of just storing one pointer in memory.

  final IString str;
  final int[][] e2f;

  private PhraseAlignment(String s) {
    //System.err.println("align: "+s);
    String stringRep = s.intern();
    if(stringRep.equals("I-I")) {
        e2f = null;
    } else {
      String[] els = stringRep.split(";");
      e2f = new int[els.length][];
      for(int i=0; i<e2f.length; ++i) {
        //System.err.printf("(%d): %s\n",i,els[i]);
        if(!els[i].equals("()")) {
          String[] els2 = els[i].split(",");
          e2f[i] = new int[els2.length];
          for(int j=0; j<e2f[i].length; ++j) {
            //System.err.printf("(%d): %s\n",j,els2[j]);
            String num = els2[j].replaceAll("[()]","");
            e2f[i][j] = Integer.parseInt(num);
          }
        }
      }
    }
    str = new IString(stringRep);
    //System.err.println(Arrays.deepToString(e2f));
  }

  @Override
  public boolean equals(Object o) {
    assert(o instanceof PhraseAlignment);
    PhraseAlignment a = (PhraseAlignment)o;
    return this.str.id == a.str.id;
  }

  @Override
  public int hashCode() {
    return str.hashCode();
  }

  public int[] e2f(int i) {
    return (e2f != null) ? e2f[i] : new int[] {i};
  }

  public static final Map<String,PhraseAlignment> map = new Object2ObjectOpenHashMap<String,PhraseAlignment>();

  public static PhraseAlignment getPhraseAlignment(String string) {
    PhraseAlignment holder = map.get(string);
    if(holder == null) {
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
    return e2f != null;
  }

  public int size() {
    return (e2f != null) ? e2f.length : 0;
  }

}
