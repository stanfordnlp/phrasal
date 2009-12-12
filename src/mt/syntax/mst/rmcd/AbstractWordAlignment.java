package mt.syntax.mst.rmcd;

import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;

import mt.base.IString;

/**
 * Abstract class representing a set of word alignments for one sentence pair.
 * It defines both source-to-target and target-to-source word aligments, which
 * are not necessarily symmetrical (since words aligners such as GIZA do not 
 * produce symmetrical word alignments). 
 *
 * @see mt.train.GIZAWordAlignment
 * @see SymmetricalWordAlignment
 *
 * @author Michel Galley
 */

public class AbstractWordAlignment implements WordAlignment {

  public static final String DEBUG_PROPERTY = "DebugWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String KEEP_BAD_TOKENS_PROPERTY = "keepBadTokens";
  public static final boolean KEEP_BAD_TOKENS
   = Boolean.parseBoolean(System.getProperty(KEEP_BAD_TOKENS_PROPERTY, "false"));

  Integer id;

  Sequence<IString> f;
  Sequence<IString> e;
  int[][] f2e;
  int[][] e2f;

  AbstractWordAlignment() {}

  AbstractWordAlignment(Sequence<IString> f, Sequence<IString> e,
                        int[][] f2e, int[][] e2f) {
    id = 0;
    this.f = f; this.e = e;
    this.f2e = f2e; this.e2f = e2f;
  }

  public Integer getId() { return id; }

  public Sequence<IString> f() { return f; }
  public Sequence<IString> e() { return e; }

  public int fSize() { return f.size(); }
  public int eSize() { return e.size(); }
  
  public int[] f2e(int i) { return f2e[i]; }
  public int[] e2f(int i) { return e2f[i]; }

  String toString(Set<Integer>[] align) {
    return toString(align,true);
  }
  
  String toString(Set<Integer>[] align, boolean zeroIndexed) {
    int o = zeroIndexed ? 0 : 1;
    StringBuffer str = new StringBuffer();
    for(int i=0; i<align.length; ++i)
      for(int j : align[i])
        str.append(i+o).append("-").append(j+o).append(" ");
    return str.toString();
  }

  /**
   * Any training data pre-processing can be applied here. 
   * Note that this pre-processing can't change the number of tokens.
   * Probably not the right place for language specific stuff.
   * @param words input sentence
   * @return output sentence
   */
  static public String[] preproc(String[] words) {
    return removeBadTokens(words);
  }

  /**
   * Convert words that may cause problems in phrase tables (for now, just '|').
   * @param words input sentence
   * @return output sentence
   */
  static public String[] removeBadTokens(String[] words) {
    if(KEEP_BAD_TOKENS)
      return words;
    for(int i=0; i<words.length; ++i) {
      if(words[i].indexOf('|') >= 0) {
        words[i] = ",";
        if(DEBUG)
          System.err.println
           ("AbstractWordAlignment: WARNING: "+
            "\"|\" converted to \";\" to avoid problems with phrase tables.");
      }
    }
    return words;
  }

  public boolean equals(Object o) {
    assert(o instanceof AbstractWordAlignment);
    AbstractWordAlignment wa = (AbstractWordAlignment)o;
    if(!f.equals(wa.f()) || !e.equals(wa.e()))
      return false;
    for(int i=0; i<f.size(); ++i)
       if(!Arrays.equals(f2e[i],wa.f2e[i]))
        return false;
    for(int i=0; i<e.size(); ++i)
      if(!Arrays.equals(e2f[i], wa.e2f[i]))
        return false;
    return true;
  }

  public int hashCode() {
    ArrayList<Integer> hs = new ArrayList<Integer>(2+f2e.length+e2f.length);
    hs.add(e().hashCode());
    hs.add(f().hashCode());
    for (int[] af2e : f2e)
      hs.add(Arrays.hashCode(af2e));
    for (int[] ae2f : e2f)
      hs.add(Arrays.hashCode(ae2f));
    return hs.hashCode();
  }

  public double ratioFtoE() {
    assert(eSize() > 0);
    return fSize()*1.0/eSize();
  }

  public boolean isAdmissiblePhraseF(int i, int j) {
    boolean empty = true;
    for(int k=i; k<=j; ++k)
      for(int ei : f2e[k]) {
        empty = false;
        for(int fi : e2f[ei])
          if(fi < i && fi > j)
            return false;
      }
    return !empty;
  }
}
