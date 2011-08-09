package edu.stanford.nlp.mt.train;

import java.util.TreeSet;
import java.io.*;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

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

  public SymmetricalWordAlignment() {
  }

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
      boolean s2t, boolean oneIndexed) throws IOException {
    init(fStr, eStr, aStr, s2t, oneIndexed);
  }

  public SymmetricalWordAlignment(String fStr, String eStr, String aStr)
      throws IOException {
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

  public void init(String fStr, String eStr, String aStr) throws IOException {
    init(fStr, eStr, aStr, false);
  }

  public void init(String fStr, String eStr, String aStr, boolean reverse)
      throws IOException {
    init(fStr, eStr, aStr, reverse, false);
  }

  protected void initSentPair(String fStr, String eStr) {
    if (addBoundaryMarkers) {
      fStr = new StringBuffer("<s> ").append(fStr).append(" </s>").toString();
      eStr = new StringBuffer("<s> ").append(eStr).append(" </s>").toString();
    }
    f = new SimpleSequence<IString>(true,
        IStrings.toSyncIStringArray(preproc(fStr.split("\\s+"))));
    e = new SimpleSequence<IString>(true,
        IStrings.toSyncIStringArray(preproc(eStr.split("\\s+"))));
  }

  public void init(String fStr, String eStr, String aStr, boolean reverse,
      boolean oneIndexed) throws IOException {
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
          throw new IOException("f has index out of bounds (fsize=" + f.size()
              + ",esize=" + e.size() + ") : " + fpos);
        if (0 > epos || epos >= e.size())
          throw new IOException("e has index out of bounds (esize=" + e.size()
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

  /**
   * Compute alignment error rate. Since there is (currently) no S vs. P
   * distinction alignment in this class, AER is 1 minus F-measure.
   * 
   * @return alignment error rate
   * @param ref
   *          reference alignment
   * @param hyp
   *          hypothesis alignment
   */
  static double computeAER(SymmetricalWordAlignment[] ref,
      SymmetricalWordAlignment[] hyp) {
    int tpC = 0, refC = 0, hypC = 0;
    double totalPrec = 0.0, totalRecall = 0.0, totalF = 0.0;
    if (ref.length != hyp.length)
      throw new RuntimeException("Not same number of aligned sentences!");
    for (int i = 0; i < ref.length; ++i) {
      int _tpC = 0, _refC = 0, _hypC = 0;
      SymmetricalWordAlignment r = ref[i], h = hyp[i];
      assert (r.f().equals(h.f()));
      assert (r.e().equals(h.e()));
      for (int j = 0; j < r.fSize(); ++j) {
        for (int k : r.f2e(j)) {
          if (h.f2e(j).contains(k))
            ++_tpC;
        }
        _refC += r.f2e(j).size();
        _hypC += h.f2e(j).size();
      }
      tpC += _tpC;
      refC += _refC;
      hypC += _hypC;
      double _prec = (_hypC > 0) ? _tpC * 1.0 / _hypC : 0;
      double _recall = (_refC > 0) ? _tpC * 1.0 / _refC : 0;
      double _f = (_prec + _recall > 0) ? 2 * _prec * _recall
          / (_prec + _recall) : 0.0;
      totalPrec += _prec;
      totalRecall += _recall;
      totalF += _f;
      if (DEBUG) {
        int len = r.f().size() + r.e().size();
        System.err.printf("sent\t%d\t%g\t%g\t%g\n", len, _prec, _recall, _f);
      }
    }
    double prec = tpC * 1.0 / hypC;
    double recall = tpC * 1.0 / refC;
    double fMeasure = 2 * prec * recall / (prec + recall);
    if (DEBUG) {
      System.err
          .printf(
              "micro: Precision = %.3g, Recall = %.3g, F = %.3g (TP=%d, HC=%d, RC=%d)\n",
              prec, recall, fMeasure, tpC, hypC, refC);
      System.err
          .printf("macro: Precision = %.3g, Recall = %.3g, F = %.3g\n",
              totalPrec / ref.length, totalRecall / ref.length, totalF
                  / ref.length);
    }
    return 1 - fMeasure;
  }

  @Override
  public String toString() {
    return toString(f2e);
  }

  public String toReverseString1() {
    return toString(e2f, false);
  }

  static public SymmetricalWordAlignment[] readFromIBMWordAlignment(
      String xmlFile) {
    InputStream in = null;
    IBMWordAlignmentHandler h = null;
    try {
      h = new IBMWordAlignmentHandler();
      in = new BufferedInputStream(new FileInputStream(new File(xmlFile)));
      h.readXML(in);
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
      }
    }
    if (h == null)
      throw new RuntimeException("Error in alignment file");
    return h.getIBMWordAlignment();
  }

  public static void main(String[] args) throws IOException {
    List<String> fLines = IOUtils.linesFromFile(args[0]), eLines = IOUtils
        .linesFromFile(args[1]), aLines = IOUtils.linesFromFile(args[2]);
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
