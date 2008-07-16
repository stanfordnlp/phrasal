package mt.train;

import java.util.Set;
import java.util.TreeSet;
import java.io.IOException;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import mt.base.Sequence;
import mt.base.SimpleSequence;

/**
 * Read in the "f2e" alignments, for two different alignment files.
 * I'm using this to input 2 differently merged files at the same time.
 * For example, aligned.intersect and aligned.grow-diag
 * 
 * @author Pi-Chuan Chang
 **/

public class DualWordAlignment implements WordAlignment {

  public static final String DEBUG_PROPERTY = "DebugDualWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private Sequence<IString> f;
  private Sequence<IString> e;
  private Set<Integer>[] alignment1_f2e;
  private Set<Integer>[] alignment2_f2e;

  DualWordAlignment() {}

  public DualWordAlignment(String fStr, String eStr, String a1Str, String a2Str) throws IOException {
    init(fStr,eStr,a1Str,a2Str);
  }

  public Integer getId() { 
    throw new UnsupportedOperationException("Not implemented."); 
  }

  @SuppressWarnings("unchecked")
  public void init(String fStr, String eStr, String a1Str, String a2Str) throws IOException {
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fStr.split("\\s+")));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(eStr.split("\\s+")));
    alignment1_f2e = new TreeSet[f.size()];
    alignment2_f2e = new TreeSet[f.size()];
    for(int i=0; i<alignment1_f2e.length; ++i)
      alignment1_f2e[i] = new TreeSet();
    for(int i=0; i<alignment2_f2e.length; ++i)
      alignment2_f2e[i] = new TreeSet();
    for(String al : a1Str.split("\\s+")) {
      String[] els = al.split("-");
      int fpos = Integer.parseInt(els[0]);
      int epos = Integer.parseInt(els[1]);
      if(0 > fpos || fpos >= f.size())
        throw new IOException("index out of bounds ("+f.size()+") : "+fpos);
      if(0 > epos || epos >= e.size())
        throw new IOException("index out of bounds ("+e.size()+") : "+epos);
      alignment1_f2e[fpos].add(epos);
      //alignment2_f2e[epos].add(fpos);
      if(DEBUG)
        System.err.println
         ("New alignment: ("+fpos+")["+f.get(fpos)+"] -> ("+epos+")["+e.get(epos)+"]");
    }

    for(String al : a2Str.split("\\s+")) {
      String[] els = al.split("-");
      int fpos = Integer.parseInt(els[0]);
      int epos = Integer.parseInt(els[1]);
      if(0 > fpos || fpos >= f.size())
        throw new IOException("index out of bounds ("+f.size()+") : "+fpos);
      if(0 > epos || epos >= e.size())
        throw new IOException("index out of bounds ("+e.size()+") : "+epos);
      //alignment1_f2e[fpos].add(epos);
      alignment2_f2e[fpos].add(epos);
      if(DEBUG)
        System.err.println
         ("New alignment: ("+fpos+")["+f.get(fpos)+"] -> ("+epos+")["+e.get(epos)+"]");
    }
    if(DEBUG)
      System.err.println("Word alignment: "+toString());
  }

  public String toString() {
    StringBuffer str = new StringBuffer();
    for(int i=0; i<f.size(); ++i)
      for(int j : f2e(i))
        str.append(i).append("-").append(j).append(" ");
    return str.toString();
  }

  public Sequence<IString> f() { return f; }
  public Sequence<IString> e() { return e; }
  public Set<Integer> f2e(int i) { return alignment1_f2e[i]; }
  public Set<Integer> e2f(int i) { return alignment2_f2e[i]; }
}
