package edu.stanford.nlp.mt.train;

import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Sentence pair with symmetrical word alignment (i.e., if e_i aligns to f_j in
 * one direction, then f_j aligns to e_i as well in the other direction). If
 * this is not what you want, use GIZAWordAlignment.
 * 
 * @author Michel Galley
 * @see WordAlignment
 * @see GIZAWordAlignment
 */

public class SymmetricalWordAlignment extends AbstractWordAlignment {

  public static final String DEBUG_PROPERTY = "DebugWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String VDEBUG_PROPERTY = "VerboseDebugWordAlignment";
  public static final boolean VERBOSE_DEBUG = Boolean.parseBoolean(System
      .getProperty(VDEBUG_PROPERTY, "false"));

  static public final String ADD_BOUNDARY_MARKERS_OPT = "addSentenceBoundaryMarkers";
  static public final String UNALIGN_BOUNDARY_MARKERS_OPT = "unalignSentenceBoundaryMarkers";

  boolean addBoundaryMarkers = false;
  boolean unalignedBoundaryMarkers = false;

  public SymmetricalWordAlignment() {}

  public SymmetricalWordAlignment(Properties prop) {
    addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(
        ADD_BOUNDARY_MARKERS_OPT, "false"));
    unalignedBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(
        UNALIGN_BOUNDARY_MARKERS_OPT, "false"));
  }

  SymmetricalWordAlignment(Sequence<IString> f, Sequence<IString> e,
      SortedSet<Integer>[] f2e, SortedSet<Integer>[] e2f) {
    super(f, e, f2e, e2f);
    if (f2e == null && e2f == null)
      initAlignment();
  }

  public SymmetricalWordAlignment(String fStr, String eStr, String aStr,
      boolean s2t, boolean oneIndexed) {
    init(fStr, eStr, aStr, s2t, oneIndexed);
  }

  public SymmetricalWordAlignment(String fStr, String eStr, String aStr) {
    init(fStr, eStr, aStr);
  }

  public SymmetricalWordAlignment(Sequence<IString> f, Sequence<IString> e) {
    this.f = f;
    this.e = e;
    initAlignment();
  }

  public void init(Integer id, String fStr, String eStr, String aStr,
      boolean reverse, boolean oneIndexed) throws IOException {
    this.id = id;
    init(fStr, eStr, aStr, reverse, oneIndexed);
  }

  public void init(String fStr, String eStr, String aStr) {
    init(fStr, eStr, aStr, false);
  }

  public void init(String fStr, String eStr, String aStr, boolean reverse) {
    init(fStr, eStr, aStr, reverse, false);
  }

  protected void initSentPair(String fStr, String eStr) {
    if (addBoundaryMarkers) {
      fStr = new StringBuffer("<s> ").append(fStr).append(" </s>").toString();
      eStr = new StringBuffer("<s> ").append(eStr).append(" </s>").toString();
    }
    f = IStrings.toIStringSequence(escape(fStr.split("\\s+")));
    e = IStrings.toIStringSequence(escape(eStr.split("\\s+")));
  }

  public void init(String fStr, String eStr, String aStr, boolean reverse,
      boolean oneIndexed) {
    if (VERBOSE_DEBUG)
      System.err.printf("f: %s\ne: %s\nalign: %s\n", fStr, eStr, aStr);
    initSentPair(fStr, eStr);
    initAlignment();
    if (aStr == null) {
      System.err.println("Warning: empty line.");
      return;
    }
    for (String al : aStr.split("\\s+")) {
      String[] els = al.split("-");
      if (els.length == 2) {
        int fpos = reverse ? Integer.parseInt(els[1]) : Integer
            .parseInt(els[0]);
        int epos = reverse ? Integer.parseInt(els[0]) : Integer
            .parseInt(els[1]);
        if (oneIndexed) {
          --fpos;
          --epos;
        }
        if (addBoundaryMarkers) {
          ++fpos;
          ++epos;
        }
        if (0 > fpos || fpos >= f.size())
          throw new RuntimeException("f has index out of bounds (fsize=" + f.size()
              + ",esize=" + e.size() + ") : " + fpos);
        if (0 > epos || epos >= e.size())
          throw new RuntimeException("e has index out of bounds (esize=" + e.size()
              + ",fsize=" + f.size() + ") : " + epos);
        f2e[fpos].add(epos);
        e2f[epos].add(fpos);
        if (VERBOSE_DEBUG) {
          System.err.println("word alignment: [" + f.get(fpos) + "] -> ["
              + e.get(epos) + "]");
          System.err.println("with indices: (" + fpos + ")[" + f.get(fpos)
              + "] -> (" + epos + ")[" + e.get(epos) + "]");
        }
      } else {
        System.err.printf("Warning: bad alignment token: <%s>\n", al);
      }
    }
    if (addBoundaryMarkers && !unalignedBoundaryMarkers) {
      int lastf = f2e.length - 1;
      int laste = e2f.length - 1;
      f2e[0].add(0);
      e2f[0].add(0);
      f2e[lastf].add(laste);
      e2f[laste].add(lastf);
    }
    if (VERBOSE_DEBUG)
      System.err.println("sentence alignment: " + toString());
  }

  @SuppressWarnings("unchecked")
  private void initAlignment() {
    f2e = new TreeSet[f.size()];
    e2f = new TreeSet[e.size()];
    for (int i = 0; i < f2e.length; ++i)
      f2e[i] = new TreeSet<Integer>();
    for (int i = 0; i < e2f.length; ++i)
      e2f[i] = new TreeSet<Integer>();
  }

  public void addAlign(int f, int e) {
    f2e[f].add(e);
    e2f[e].add(f);
  }
  
  public void removeAlign(int f, int e) {
    f2e[f].remove(e);
    e2f[e].remove(f);
  }

  @Override
  public String toString() {
    return toString(f2e);
  }

  public String toReverseString() {
    return toString(e2f);
  }
  
  public String toReverseString1() {
    return toString(e2f, false);
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    List<String> fLines = Files.readAllLines(Paths.get(args[0])), 
        eLines = Files.readAllLines(Paths.get(args[1])), 
        aLines = Files.readAllLines(Paths.get(args[2]));
    for (int i = 0; i < eLines.size(); ++i) {
      System.err.printf("Line %d\n", i);
      System.out.printf("f-sent: %s\n", fLines.get(i));
      System.out.printf("e-sent: %s\n", eLines.get(i));
      System.out.printf("align: %s\n", aLines.get(i));
      try {
        AbstractWordAlignment wa = new SymmetricalWordAlignment(fLines.get(i),
            eLines.get(i), aLines.get(i));
        for (int j = 0; j < wa.eSize(); ++j) {
          System.out.printf("%s {", wa.e().get(j));
          boolean first = true;
          for (int fi : wa.e2f(j)) {
            if (!first)
              System.out.print(",");
            first = false;
            System.out.printf("%s", wa.f().get(fi));
          }
          System.out.print("} ");
        }
        System.out.println();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

}
