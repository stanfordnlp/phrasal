package edu.stanford.nlp.mt.train;

import java.util.BitSet;
import java.util.TreeSet;
import java.util.SortedSet;
import java.io.IOException;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

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
  private SortedSet<Integer>[] alignment1_f2e;
  private SortedSet<Integer>[] alignment2_f2e;

  DualWordAlignment() {}

  @SuppressWarnings("unused")
  public DualWordAlignment(String fStr, String eStr, String a1Str, String a2Str) throws IOException {
    init(fStr,eStr,a1Str,a2Str);
  }

  @Override
  public Integer getId() {
    throw new UnsupportedOperationException("Not implemented."); 
  }

  @Override public BitSet unalignedE() { throw new UnsupportedOperationException(); }
  @Override public BitSet unalignedF() { throw new UnsupportedOperationException(); }

  @SuppressWarnings("unchecked")
  public void init(String fStr, String eStr, String a1Str, String a2Str) throws IOException {
    f = new SimpleSequence<IString>(true, IStrings.toSyncIStringArray(fStr.split("\\s+")));
    e = new SimpleSequence<IString>(true, IStrings.toSyncIStringArray(eStr.split("\\s+")));
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

  @Override
	public String toString() {
    StringBuilder str = new StringBuilder();
    for(int i=0; i<f.size(); ++i)
      for(int j : f2e(i))
        str.append(i).append("-").append(j).append(" ");
    return str.toString();
  }

  @Override public Sequence<IString> f() { return f; }
  @Override public Sequence<IString> e() { return e; }
  @Override public SortedSet<Integer> f2e(int i) { return alignment1_f2e[i]; }
  @Override public SortedSet<Integer> e2f(int i) { return alignment2_f2e[i]; }

  @Override public int f2eSize(int i, int min, int max) { throw new UnsupportedOperationException(); }
  @Override public int e2fSize(int i, int min, int max) { throw new UnsupportedOperationException(); }
}
