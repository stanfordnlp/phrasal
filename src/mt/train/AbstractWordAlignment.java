package mt.train;

import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.TreeSet;

import edu.stanford.nlp.util.IString;

import mt.base.Sequence;

/**
 * Abstract class representing a set of word alignments for one sentence pair.
 * It defines both source-to-target and target-to-source word aligments, which
 * are not necessarily symmetrical (since words aligners such as GIZA do not 
 * produce symmetrical word alignments). 
 *
 * @see GIZAWordAlignment
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
  Set<Integer>[] f2e;
  Set<Integer>[] e2f;

  AbstractWordAlignment() {}

  AbstractWordAlignment(Sequence<IString> f, Sequence<IString> e,
                        Set<Integer>[] f2e, Set<Integer>[] e2f) {
    id = 0;
    this.f = f; this.e = e;
    this.f2e = f2e; this.e2f = e2f;
  }

  public Integer getId() { return id; }

  public Sequence<IString> f() { return f; }
  public Sequence<IString> e() { return e; }

  public int fSize() { return f.size(); }
  public int eSize() { return e.size(); }
  
  public Set<Integer> f2e(int i) { return f2e[i]; }
  public Set<Integer> e2f(int i) { return e2f[i]; }

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
  public String[] preproc(String[] words) {
    return removeBadTokens(words);
  }

  /**
   * Convert words that may cause problems in phrase tables (for now, just '|').
   * @param words input sentence
   * @return output sentence
   */
  public String[] removeBadTokens(String[] words) {
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
       if(!f2e[i].equals(wa.f2e[i]))
        return false;
    for(int i=0; i<e.size(); ++i)
      if(!e2f[i].equals(wa.e2f[i]))
        return false;
    return true;
  }

  public int hashCode() {
    ArrayList<Integer> hs = new ArrayList<Integer>(2+f2e.length+e2f.length);
    hs.add(e().hashCode());
    hs.add(f().hashCode());
    for (Set<Integer> af2e : f2e)
      hs.add(Arrays.hashCode(af2e.toArray()));
    for (Set<Integer> ae2f : e2f)
      hs.add(Arrays.hashCode(ae2f.toArray()));
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

  /**
   * Initialize alignment using a matrix in LDC format (such as the ones
   * used in parallel treebanks. Convention: 1-indexed words, and index zero
   * reseved for unaligned words.
   * @param matrix
   */
  @SuppressWarnings("unchecked")
  public void init(int[][] matrix) {
    
    f2e = new TreeSet[matrix[0].length-1];
    for(int i=0; i<f2e.length; ++i)
      f2e[i] = new TreeSet<Integer>();

    e2f = new TreeSet[matrix.length-1];
    for(int i=0; i<e2f.length; ++i)
      e2f[i] = new TreeSet<Integer>();

    for(int i=1; i<matrix.length; ++i)
      for(int j=1; j<matrix[0].length; ++j)
        if(matrix[i][j] != 0) {
          e2f[i-1].add(j-1);
          f2e[j-1].add(i-1);
        }
  }
}
