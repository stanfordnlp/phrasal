package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Compute AER from four files: two corpus files, one reference alignment, and
 * one machine alignment. Note that the two corpora are only needed to determine
 * sentence lengths.
 * 
 * @author Michel Galley
 */
public class AER {

  public static final String DEBUG_PROPERTY = "DebugWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

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
  private static double computeAER(SymmetricalWordAlignment[] ref,
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
  
  /**
   * Compute Alignment Error Rate (AER).
   * 
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 6) {
      System.err
          .println("Usage: AER <fCorpus> <eCorpus> <refAlignment> <refUCB> <hypAlignment> <hypUCB>");
      System.err.println("where:");
      System.err.println("  fCorpus: source-language text");
      System.err.println("  eCorpus: target-language text");
      System.err.println("  refAlignment: reference word alignment");
      System.err
          .println("  refUCB: set to true if refAlignment in UCB alignment format (default: Moses format)");
      System.err.println("  hypAlignment: system word alignment");
      System.err
          .println("  hypUCB: set to true if hypAlignment in UCB alignment format (default: Moses format)");
      System.err.println("Wrong number of args: " + args.length);
      System.exit(1);
    }
    boolean r_s2t = true, r_zeroBased = false, h_s2t = true, h_zeroBased = false;
    if (Boolean.parseBoolean(args[3])) {
      r_s2t = false;
      r_zeroBased = true;
    }
    if (Boolean.parseBoolean(args[5])) {
      h_s2t = false;
      h_zeroBased = true;
    }
    Iterator<String> fCorpus = ObjectBank.getLineIterator(args[0]).iterator(), eCorpus = ObjectBank
        .getLineIterator(args[1]).iterator(), rCorpus = ObjectBank
        .getLineIterator(args[2]).iterator(), hCorpus = ObjectBank
        .getLineIterator(args[4]).iterator();
    List<SymmetricalWordAlignment> rAlign = new ArrayList<SymmetricalWordAlignment>();
    List<SymmetricalWordAlignment> hAlign = new ArrayList<SymmetricalWordAlignment>();
    int lineNb = 0;
    while (fCorpus.hasNext()) {
      ++lineNb;
      System.err.println("line : " + lineNb);
      String fLine = fCorpus.next();
      String eLine = eCorpus.next();
      String rLine = rCorpus.next();
      String hLine = hCorpus.next();
      assert (eLine != null && rLine != null && hLine != null);
      boolean isHyp = false;
      try {
        rAlign.add(new SymmetricalWordAlignment(fLine, eLine, rLine, r_s2t,
            r_zeroBased));
        isHyp = true;
        hAlign.add(new SymmetricalWordAlignment(fLine, eLine, hLine, h_s2t,
            h_zeroBased));
      } catch (RuntimeException e) {
        System.err.printf("Error at line: %d\n", lineNb);
        System.err.printf("ref: %s\neline: %s\nfline: %s\naline: %s\n", !isHyp,
            fLine, eLine, (isHyp ? hLine : rLine));
        e.printStackTrace();
        System.exit(1);
      }
    }
    assert (!eCorpus.hasNext());
    assert (!rCorpus.hasNext());
    assert (!hCorpus.hasNext());
    System.out.println(computeAER(
        rAlign.toArray(new SymmetricalWordAlignment[rAlign.size()]),
        hAlign.toArray(new SymmetricalWordAlignment[hAlign.size()])));
  }
}
