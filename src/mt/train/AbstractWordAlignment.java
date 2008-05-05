package mt.train;

import java.util.Set;

import mt.base.IString;
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
   */
  public String[] preproc(String[] words) {
    return removeBadTokens(words);
  }

  /**
   * Convert words that may cause problems in phrase tables (for now, just '|').
   */
  public String[] removeBadTokens(String[] words) {
    if(KEEP_BAD_TOKENS)
      return words;
    for(int i=0; i<words.length; ++i) {
      if(words[i].indexOf('|') >= 0) {
        System.err.println
         ("SymmetricalWordAlignment: WARNING: "+
          "\"|\" converted to \";\" to avoid problems with phrase tables.");
        words[i] = ",";
      }
    }
    return words;
  }
}
