package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.util.IOTools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.SortedSet;

/**
 * Alignment symmetrization algorithms. Produce the same outputs as symal.cpp in
 * Moses.
 * 
 * @author Michel Galley
 */

public class AlignmentSymmetrizer {

  private static final boolean DEBUG = false;

  private static final int[][] GROW_NEIGHBORS = { { -1, 0 }, { 0, -1 },
      { 1, 0 }, { 0, 1 } };
  private static final int[][] GROW_DIAG_NEIGHBORS = { { -1, 0 }, { 0, -1 },
      { 1, 0 }, { 0, 1 }, { -1, -1 }, { -1, 1 }, { 1, -1 }, { 1, 1 } };

  public enum SymmetrizationType {
    none, intersection, grow, grow_diag, grow_diag_final, grow_diag_final_and, union, srctotgt, tgttosrc
  }

  public static void symmetrizeA3Files(String feAlignFile, String efAlignFile,
      String typeName) {

    SymmetrizationType type = SymmetrizationType.valueOf(typeName);
    LineNumberReader feReader, efReader;
    GIZAWordAlignment sent = new GIZAWordAlignment();

    try {
      feReader = IOTools.getReaderFromFile(feAlignFile);
      efReader = IOTools.getReaderFromFile(efAlignFile);
      String feLine1, feLine2, feLine3, efLine1, efLine2, efLine3;
      while (true) {
        feLine1 = feReader.readLine();
        efLine1 = efReader.readLine();
        if (feLine1 == null || efLine1 == null) {
          if (feLine1 != null || efLine1 != null)
            throw new IOException("Not same number of lines!");
          break;
        }
        feLine2 = feReader.readLine();
        efLine2 = efReader.readLine();
        feLine3 = feReader.readLine();
        efLine3 = efReader.readLine();
        sent.init(feLine1, feLine2, feLine3, efLine1, efLine2, efLine3);
        if (DEBUG) {
          System.err.println("fe: " + feLine3);
          System.err.println("ef: " + efLine3);
          System.err.println(sent.toString(false));
          System.err.println(sent.toString(true));
        }
        SymmetricalWordAlignment sym = symmetrize(sent, type);
        System.out.println(sym.toString());
      }
      feReader.close();
      efReader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static SymmetricalWordAlignment symmetrize(GIZAWordAlignment in) {
    return symmetrize(in, SymmetrizationType.grow_diag_final);
  }

  public static SymmetricalWordAlignment symmetrize(GIZAWordAlignment in,
      SymmetrizationType type) {

    SymmetricalWordAlignment out = new SymmetricalWordAlignment(in.f, in.e,
        null, null);

    switch (type) {
    case intersection:
      intersection(in, out);
      break;
    case grow:
      growDiag(in, out, GROW_NEIGHBORS);
      break;
    case grow_diag:
      growDiag(in, out, GROW_DIAG_NEIGHBORS);
      break;
    case grow_diag_final:
      growDiagFinal(in, out);
      break;
    case grow_diag_final_and:
      growDiagFinalAnd(in, out);
      break;
    case union:
      addAlignment(in.f2e, false, out);
      addAlignment(in.e2f, true, out);
      break;
    case srctotgt:
      addAlignment(in.f2e, false, out);
      break;
    case tgttosrc:
      addAlignment(in.e2f, true, out);
      break;
    default:
      throw new UnsupportedOperationException(
          "Unsupported alignment symmetrization algorithm: " + type);
    }
    return out;
  }

  private static void intersection(AbstractWordAlignment in,
      SymmetricalWordAlignment out) {
    for (int fi = 0; fi < in.f2e.length; ++fi) {
      for (int ei : in.f2e[fi]) {
        if (in.e2f[ei].contains(fi)) {
          out.e2f[ei].add(fi);
          out.f2e[fi].add(ei);
        }
      }
    }
  }

  /**
   * Note that, as for the other symmetrization algorithms implemented in this
   * class, growDiag faithfully reproduces the implementation in Moses
   * (including a somewhat undesirable property: reverse(symmetrize(f,e)) ==
   * symmetrize(e,f)) is not always true.
   */
  private static void growDiag(AbstractWordAlignment in,
      SymmetricalWordAlignment out, int[][] neighbors) {

    assert (out.isEmpty());

    intersection(in, out);

    boolean redo = true;
    while (redo) {
      redo = false;

      // grow-diag algorithm:
      for (int ei = 0; ei < out.e2f.length; ++ei) {
        // Copy contents of TreeSet out.e2f[ei], so that we can add elements to
        // it during iteration:
        Integer[] e2fc = out.e2f[ei].toArray(new Integer[out.e2f[ei].size()]);
        for (int fi : e2fc) {
          for (int[] neighbor : neighbors) {
            int nfi = fi + neighbor[0];
            int nei = ei + neighbor[1];
            if (nfi < 0 || nei < 0 || nfi >= out.f2e.length
                || nei >= out.e2f.length)
              continue;
            if (!out.f2e[nfi].contains(nei)) {
              if (out.f2e[nfi].isEmpty() || out.e2f[nei].isEmpty()) {
                if (in.f2e[nfi].contains(nei) || in.e2f[nei].contains(nfi)) {
                  out.e2f[nei].add(nfi);
                  out.f2e[nfi].add(nei);
                  redo = true;
                }
              }
            }
          }
        }
      }
    }
  }

  private static void growDiagFinal(AbstractWordAlignment in,
      SymmetricalWordAlignment out) {
    assert (out.isEmpty());
    growDiag(in, out, GROW_DIAG_NEIGHBORS);
    runFinal(in, false, out);
    runFinalInv(in, false, out);
  }

  private static void growDiagFinalAnd(AbstractWordAlignment in,
      SymmetricalWordAlignment out) {
    assert (out.isEmpty());
    growDiag(in, out, GROW_DIAG_NEIGHBORS);
    runFinal(in, true, out);
    runFinalInv(in, true, out);
  }

  private static void runFinal(AbstractWordAlignment in, boolean both,
      SymmetricalWordAlignment out) {
    for (int ei = 0; ei < in.e2f.length; ++ei) {
      for (int fi : in.e2f[ei]) {
        if (!out.f2e[fi].contains(ei)) {
          boolean fE = out.f2e[fi].isEmpty(), eE = out.e2f[ei].isEmpty();
          if ((both && (fE && eE)) || (!both && (fE || eE))) {
            out.e2f[ei].add(fi);
            out.f2e[fi].add(ei);
          }
        }
      }
    }
  }

  private static void runFinalInv(AbstractWordAlignment in, boolean both,
      SymmetricalWordAlignment out) {
    for (int fi = 0; fi < in.f2e.length; ++fi) {
      for (int ei : in.f2e[fi]) {
        if (!out.f2e[fi].contains(ei)) {
          boolean fE = out.f2e[fi].isEmpty(), eE = out.e2f[ei].isEmpty();
          if ((both && (fE && eE)) || (!both && (fE || eE))) {
            out.e2f[ei].add(fi);
            out.f2e[fi].add(ei);
          }
        }
      }
    }
  }

  private static void addAlignment(SortedSet<Integer>[] f2e, boolean reverse,
      SymmetricalWordAlignment sym) {
    for (int fi = 0; fi < f2e.length; ++fi) {
      for (int ei : f2e[fi]) {
        if (reverse) {
          sym.e2f[fi].add(ei);
          sym.f2e[ei].add(fi);
        } else {
          sym.e2f[ei].add(fi);
          sym.f2e[fi].add(ei);
        }
      }
    }
  }

  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.printf(
          "Usage: java %s (f-e.A3) (e-f.A3) (symmetrization heuristic)\n",
          AlignmentSymmetrizer.class.getName());
      System.exit(-1);
    }
    String type = args[2];
    type = type.replace('-', '_');
    symmetrizeA3Files(args[0], args[1], type);
  }

}
