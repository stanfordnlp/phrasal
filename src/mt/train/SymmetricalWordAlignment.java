package mt.train;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.io.IOException;

import mt.base.IString;
import mt.base.IStrings;
import mt.base.Sequence;
import mt.base.SimpleSequence;

/**
  * Sentence pair with symmetrical word alignment (i.e., if e_i aligns to f_j in one direction, 
  * then f_j aligns to e_i as well in the other direction). If this is not what you want, use
  * GIZAWordAlignment.
  * 
  * @author Michel Galley
  * @see WordAlignment
  * @see GIZAWordAlignment
  */

public class SymmetricalWordAlignment extends AbstractWordAlignment {

  public static final String DEBUG_PROPERTY = "DebugSymmetricalWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public SymmetricalWordAlignment() {
    System.err.println("SymmetricalWordAlignment: new instance.");
  }

  public SymmetricalWordAlignment(Integer id, String fStr, String eStr, String aStr) throws IOException {
    init(id,fStr,eStr,aStr);
  }
  public SymmetricalWordAlignment(String fStr, String eStr, String aStr) throws IOException {
    init(fStr,eStr,aStr);
  }

  public SymmetricalWordAlignment(Sequence<IString> f, Sequence<IString> e) {
    this.f = f; this.e = e;
    initAlignment();
  }

  public void init(Integer id, String fStr, String eStr, String aStr) throws IOException {
    this.id = id;
    init(fStr,eStr,aStr);
  }

  public void init(String fStr, String eStr, String aStr) throws IOException {
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(preproc(fStr.split("\\s+"))));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(preproc(eStr.split("\\s+"))));
    initAlignment();
    for(String al : aStr.split("\\s+")) {
      String[] els = al.split("-");
      int fpos = Integer.parseInt(els[0]);
      int epos = Integer.parseInt(els[1]);
      if(0 > fpos || fpos >= f.size())
        throw new IOException("index out of bounds ("+f.size()+") : "+fpos);
      if(0 > epos || epos >= e.size())
        throw new IOException("index out of bounds ("+e.size()+") : "+epos);
      f2e[fpos].add(epos);
      e2f[epos].add(fpos);
      if(DEBUG)
        System.err.println
         ("New alignment: ("+fpos+")["+f.get(fpos)+"] -> ("+epos+")["+e.get(epos)+"]");
    }
    if(DEBUG)
      System.err.println("Word alignment: "+toString());
  }

  @SuppressWarnings("unchecked")
  private void initAlignment() {
    f2e = new TreeSet[f.size()];
    e2f = new TreeSet[e.size()];
    for(int i=0; i<f2e.length; ++i)
      f2e[i] = new TreeSet();
    for(int i=0; i<e2f.length; ++i)
      e2f[i] = new TreeSet();
  }
  
  public void addAlign(int f, int e) {
    f2e[f].add(e);
    e2f[e].add(f);
  }

  public String toString() {
    return toString(f2e);
  }
}
