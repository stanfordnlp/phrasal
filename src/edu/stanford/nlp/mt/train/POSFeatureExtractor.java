package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.DTUTable;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.IString;

import it.unimi.dsi.fastutil.ints.AbstractIntList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Extractor for the 4 POS translation probabilities features 
 * TODO(mengqiu) refactor this code to remove duplication with MosesPharoahFeatureExtractor
 * @author Mengqiu Wang
 */
public class POSFeatureExtractor extends AbstractFeatureExtractor implements
    PhrasePrinter {

  public static final double MIN_LEX_PROB = 0;

  public static final double DEFAULT_PHI_FILTER = 0;
  public static final double DEFAULT_LEX_FILTER = 0;

  public static final String DEBUG_PROPERTY = "DebugPOSFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));

  public static final String PRINT_COUNTS_PROPERTY = "DebugPrintCounts";
  public static final boolean PRINT_COUNTS = Boolean.parseBoolean(System
      .getProperty(PRINT_COUNTS_PROPERTY, "false"));

  static public final String POS_PHI_FILTER_OPT = "posPhiFilter"; // p_pos_phi(e|f)
                                                                  // filtering
  static public final String POS_LEX_FILTER_OPT = "posLexFilter"; // p_pos_lex(e|f)
                                                                  // filtering
  protected double phiFilter = DEFAULT_PHI_FILTER,
      lexFilter = DEFAULT_LEX_FILTER;
  protected boolean inputPOSTagged = false;
  protected boolean usePmi, pmiSquare = false;
  protected int numPasses = 1;

  protected final DynamicIntegerArrayIndex lexIndex = new DynamicIntegerArrayIndex();
  protected final Index<Integer> fLexIndex = new HashIndex<Integer>(),
      eLexIndex = new HashIndex<Integer>();
  protected final DynamicIntegerArrayIndex phiIndex = new DynamicIntegerArrayIndex();
  protected final DynamicIntegerArrayIndex fPhiIndex = new DynamicIntegerArrayIndex();
  protected final DynamicIntegerArrayIndex ePhiIndex = new DynamicIntegerArrayIndex();

  protected final IntArrayList feCounts = new IntArrayList();
  protected final IntArrayList fCounts = new IntArrayList();
  protected final IntArrayList eCounts = new IntArrayList();

  protected final IntArrayList feLexCounts = new IntArrayList();
  protected final IntArrayList fLexCounts = new IntArrayList();
  protected final IntArrayList eLexCounts = new IntArrayList();

  public static final IString NULL_STR = new IString("NULL");

  @Override
  public void init(Properties prop, Index<String> featureIndex,
      AlignmentTemplates alTemps) {
    super.init(prop, featureIndex, alTemps);
    // Set counts of "NULL":
    fLexCounts.add(0);
    eLexCounts.add(0);
    // Do we want exact counts?
    boolean exact = Boolean.parseBoolean(prop.getProperty(PhraseExtract.EXACT_PHI_OPT, "true"));
    this.numPasses = exact ? 2 : 1;
    System.err.println("Exact denominator counts for phi(f|e): " + exact);
    // IBM lexicalized model?
    alTemps.enableAlignmentCounts(true);
    inputPOSTagged = Boolean.parseBoolean(prop.getProperty(
        PhraseExtract.INPUT_POS_TAGGED_OPT, "false"));
    usePmi = Boolean.parseBoolean(prop.getProperty(PhraseExtract.USE_PMI, "false"));
    pmiSquare = Boolean.parseBoolean(prop.getProperty(PhraseExtract.PMI_SQUARE, "false"));
    // Filtering:
    phiFilter = Double.parseDouble(prop.getProperty(
        POS_PHI_FILTER_OPT,
        Double.toString(DEFAULT_PHI_FILTER)));
    lexFilter = Double.parseDouble(prop.getProperty(
        POS_LEX_FILTER_OPT,
        Double.toString(DEFAULT_LEX_FILTER)));
    System.err.printf("Cut-off value for phi_pos(e|f): %.5f\n", phiFilter);
    System.err.printf("Cut-off value for lex_pos(e|f): %.5f\n", lexFilter);
  }

  @Override
  public int getRequiredPassNumber() {
    return numPasses;
  }

  @Override
  public void featurizeSentence(SymmetricalWordAlignment sent, String info,
      AlignmentGrid alGrid) {
    if (!inputPOSTagged)
      return;
    // Increment word counts:
    Sequence<IString> f = sent.fPOS();
    Sequence<IString> e = sent.ePOS();
    for (int fi = 0; fi < f.size(); ++fi) {
      // Word pair counts (f,e):
      for (int ei : sent.f2e(fi))
        addLexCount(f.get(fi), e.get(ei));
      // Unaligned foreign words (f,NULL):
      if (sent.f2e(fi).isEmpty())
        addLexCount(f.get(fi), NULL_STR);
    }
    // Unaligned English words (NULL,e):
    for (int ei = 0; ei < e.size(); ++ei)
      if (sent.e2f(ei).isEmpty())
        addLexCount(NULL_STR, e.get(ei));
  }

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
    if (!inputPOSTagged)
      return;
    // Code below will only get executed during the last pass:
    if (getCurrentPass() + 1 == getRequiredPassNumber()) {
      if (DEBUG_LEVEL >= 2)
        System.err.println("Adding POS phrase to table: "
            + alTemp.fPOS().toString(" ") + " -> " + alTemp.ePOS().toString(" "));
      // Increment phrase counts c(f,e), c(f), c(e):
      addPhiCount(alTemp);
    }
  }

  /**
   * Print the five translation model features that appear in Moses' phrase
   * tables.
   */
  @Override
  public Object score(AlignmentTemplate alTemp) {
    if (!inputPOSTagged)
      return null;
    // print phi_pos(f|e), lex_pos(f|e), phi_pos(e|f), lex_pos(e|f), phi_pmi^2(f,e) 
    int[] idxs = getIdx(alTemp);
    int idx = idxs[0];
    int idxF = idxs[1]; 
    int idxE = idxs[2];
    //int idx = alTemp.getPosKey();
    //int idxF = alTemp.getPosFKey();
    //int idxE = alTemp.getPosEKey();
    if (idx >= feCounts.size() || idxF >= fCounts.size()
        || idxE >= eCounts.size()) {
      System.err.println("can't get Pharaoh POS translation features for phrase: "
          + alTemp.toString(true));
      return null;
    }
    assert (idx >= 0 && idxF >= 0 && idxE >= 0) : " bad idx="+idx+", idxF="+idxF+", idxE="+idxE;
    // Compute phi features p(f|e) and p(e|f):
    double pairCount = feCounts.get(idx); 
    double eCount = eCounts.get(idxE);
    double fCount = fCounts.get(idxF);
    double phi_f_e = pairCount * 1.0 / eCount;
    double phi_e_f = pairCount * 1.0 / fCount;

    if (phiFilter > phi_e_f)
      return null;
    double pmi = phi_f_e / fCount;
    if (pmiSquare)
      pmi = pmi * pairCount;
    // Compute lexical weighting features:
    double lex_f_e;
    double lex_e_f;
    //if (ibmLexModel) {
    //  lex_f_e = getIBMLexScore(alTemp);
    //  lex_e_f = getIBMLexScoreInv(alTemp);
    //} else {
      lex_f_e = getLexScore(alTemp);
      lex_e_f = getLexScoreInv(alTemp);
    //}
    double lexPMI = getLexPmiScore(alTemp);
    // Determine if need to filter phrase:
    if (lexFilter > lex_e_f)
      return null;

    if (PRINT_COUNTS) {
      // -- Additional info for debugging purposes:
      return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f, 
          feCounts.get(idx), eCounts.get(idxE), fCounts.get(idxF) };
    } else if (usePmi) {
      // return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f, pmi, lexPMI };
      return new double[] { pmi, lexPMI };
    } else {
      // -- 5 basic features functions of Moses minus phrasePenalty:
      return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f };
    }
  }

  private int[] getIdx(AlignmentTemplate alTemp) {
    Sequence<IString> f = alTemp.fPOS();
    Sequence<IString> e = alTemp.ePOS();
    assert (f != null) : "alTemp.fPOS is null";
    assert (e != null) : "alTemp.ePOS is null";
    int idxF = indexOfFPhi(f, true);
    int idxE = indexOfEPhi(e, true);
    int idxFE = indexOfPhi(idxF, idxE, true);
    return new int[] {idxFE, idxF, idxE};
  }

  private void addPhiCount(AlignmentTemplateInstance alTemp) {
    if (DEBUG_LEVEL >= 2)
      System.err.println("Adding phrase alignment count: c(f = " + alTemp.fPOS().toString(" ") + ", e=" + alTemp.ePOS().toString(" ") + ")");
    // Increment word counts for lexical weighting:
    int[] idxs = getIdx(alTemp);
    addCountToArray(feCounts, idxs[0]);
    addCountToArray(fCounts, idxs[1]);
    addCountToArray(eCounts, idxs[2]);
    // alTemp.setPosKey(idxs[0]);
    // alTemp.setPosFKey(idxs[1]);
    // alTemp.setPosFKey(idxs[2]);
  }

  private int indexOfPhi(int idxF, int idxE, boolean add) {
    synchronized (phiIndex) {
      return phiIndex.indexOf(new int[] { idxF, idxE }, add);
    }
  }

  private int indexOfFPhi(Sequence<IString> f, boolean add) {
    assert (f != null) : "in indexOfFPhi, f is null";
    synchronized (fPhiIndex) {
      return fPhiIndex.indexOf(Sequences.toIntArray(f), add);
    }
  }

  private int indexOfEPhi(Sequence<IString> e, boolean add) {
    assert (e != null) : "in indexOfEPhi, e is null";
    synchronized (ePhiIndex) {
      return ePhiIndex.indexOf(Sequences.toIntArray(e), add);
    }
  }

  private void addLexCount(IString f, IString e) {
    if (DEBUG_LEVEL >= 2)
      System.err.println("Adding lexical alignment count: c(f = " + f + "("
          + f.getId() + "), e=" + e + " (" + e.getId() + "))");
    // Increment word counts for lexical weighting:
    addCountToArray(feLexCounts, indexOfLex(f, e, true));
    addCountToArray(fLexCounts, indexOfFLex(f, true));
    addCountToArray(eLexCounts, indexOfELex(e, true));
  }

  private int indexOfLex(IString f, IString e, boolean add) {
    return lexIndex.indexOf(new int[] { f.getId(), e.getId() }, add);
  }

  private int indexOfFLex(IString f, boolean add) {
    synchronized (fLexIndex) {
      return fLexIndex.indexOf(f.getId(), add);
    }
  }

  private int indexOfELex(IString e, boolean add) {
    synchronized (eLexIndex) {
      return eLexIndex.indexOf(e.getId(), add);
    }
  }

  private static void addCountToArray(AbstractIntList list, int idx) {
    if (idx < 0)
      return;
    synchronized (list) {
      while (idx >= list.size())
        list.add(0);
      int newCount = list.get(idx) + 1;
      list.set(idx, newCount);
    }
    if (DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx=" + idx + " in vector (" + list
          + ").");
  }


  /**
   * Lexically-weighted probability of alTemp.fPOS() given alTemp.ePOS() according to
   * Moses.
   */
  private double getLexScore(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(f|e) for alignment template: "
          + alTemp.toString(true));
    // Each French word must be explained:
    double lex = 1.0;
    for (int fi = 0; fi < alTemp.fPOS().size(); ++fi) {
      if (alTemp.fPOS().get(fi).equals(DTUTable.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.f2e(fi).size();
      if (alCount == 0) {
        wSum = getLexProb(alTemp.fPOS().get(fi), NULL_STR);
      } else {
        for (int ei : alTemp.f2e(fi)) {
          assert (!alTemp.ePOS().get(ei).equals(DTUTable.GAP_STR));
          wSum += getLexProb(alTemp.fPOS().get(fi), alTemp.ePOS().get(ei));
        }
        wSum /= alCount;
      }
      if (DEBUG_LEVEL >= 1 || wSum == 0.0) {
        System.err.printf("w(%s|...) = %.3f\n", alTemp.fPOS().get(fi), wSum);
        if (wSum == 0)
          System.err.println("  WARNING: wsum = " + wSum);
      }
      if (wSum == 0)
        wSum = MIN_LEX_PROB;
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Lexically-weighted probability of alTemp.ePOS() given alTemp.fPOS() according to
   * Moses.
   */
  private double getLexScoreInv(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(e|f) for alignment template: "
          + alTemp.toString());
    // Each English word must be explained:
    double lex = 1.0;
    for (int ei = 0; ei < alTemp.ePOS().size(); ++ei) {
      if (alTemp.ePOS().get(ei).equals(DTUTable.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.e2f(ei).size();
      if (alCount == 0) {
        wSum += getLexProbInv(NULL_STR, alTemp.ePOS().get(ei));
      } else {
        for (int fi : alTemp.e2f(ei)) {
          wSum += getLexProbInv(alTemp.fPOS().get(fi), alTemp.ePOS().get(ei));
        }
        wSum /= alCount;
      }
      if (DEBUG_LEVEL >= 1 || wSum == 0.0)
        System.err.printf("w(%s|...) = %.3f\n", alTemp.ePOS().get(ei), wSum);
      if (wSum == 0)
        wSum = MIN_LEX_PROB;
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Lexically-weighted PMI as the sum of alTemp.fPOS() given alTemp.ePOS() 
   */
  private double getLexPmiScore(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing word-level PMI(f|e) for alignment template: "
          + alTemp.toString(true));
    // PMI calculation only accounts for French words that are aligned,
    double pmiSum = 1.0;
    for (int fi = 0; fi < alTemp.fPOS().size(); ++fi) {
      if (alTemp.fPOS().get(fi).equals(DTUTable.GAP_STR))
        continue;
      double wPMI = 0.0;
      int alCount = alTemp.f2e(fi).size();
      if (alCount == 0) {
        wPMI += getLexPmi(alTemp.fPOS().get(fi), NULL_STR);
      } else {
        for (int ei : alTemp.f2e(fi)) {
          wPMI += getLexPmi(alTemp.fPOS().get(fi), alTemp.ePOS().get(ei));
        }
        wPMI /= alCount;
      }
      if (wPMI == 0)
        wPMI = MIN_LEX_PROB;
      pmiSum *= wPMI;
    }
    return pmiSum;
  }


  /**
   * Point-wise Mutual Information of f and e. Note that getLexPmi(f,e)
   * should equal to getLexPmi(e,f)
   */
  private double getLexPmi(IString f, IString e) {
    if (DEBUG_LEVEL >= 1) {
      System.err.print("pmi(f = \"" + f + "\" | e = \"" + e + "\") = ");
      System.err.print(feLexCounts.get(indexOfLex(f, e, false)));
      System.err.print("/(");
      System.err.println(eLexCounts.get(indexOfELex(e, false)));
      System.err.print("*");
      System.err.println(fLexCounts.get(indexOfFLex(f, false)));
      System.err.print(")");
    }
    int fei = indexOfLex(f, e, false);
    int ei = indexOfELex(e, false);
    int fi = indexOfFLex(f, false);
    if (fei < 0 || ei < 0 | fi < 0) // this is a not very well defined case
      return 0.0;
    double pFE = feLexCounts.get(fei);
    double pF = eLexCounts.get(ei);
    double pE = fLexCounts.get(fi);
    double lexPMI = pFE / (pF * pE);
    if (pmiSquare)
      lexPMI = lexPMI * pFE;
    return lexPMI;
  }

  /**
   * Word translation probability of f given e. Note that getLexProb(f,e)
   * generally does not return the same as getLexProbInv(e,f), since
   * normalization counts are different.
   */
  private double getLexProb(IString f, IString e) {
    if (DEBUG_LEVEL >= 1) {
      System.err.print("p(f = \"" + f + "\" | e = \"" + e + "\") = ");
      System.err.print(feLexCounts.get(indexOfLex(f, e, false)));
      System.err.print("/");
      System.err.println(eLexCounts.get(indexOfELex(e, false)));
    }
    int fei = indexOfLex(f, e, false);
    int ei = indexOfELex(e, false);
    if (fei < 0 || ei < 0)
      return 0.0;
    return feLexCounts.get(fei) * 1.0 / eLexCounts.get(ei);
  }

  /**
   * Word translation probability of e given f. Note that getLexProb(f,e)
   * generally does not return the same as getLexProbInv(e,f), since
   * normalization counts are different.
   */
  private double getLexProbInv(IString f, IString e) {
    if (DEBUG_LEVEL >= 1) {
      System.err.print("p(e = \"" + e + "\" | f = \"" + f + "\") = ");
      System.err.print(feLexCounts.get(indexOfLex(f, e, false)));
      System.err.print("/");
      System.err.println(fLexCounts.get(indexOfFLex(f, false)));
    }
    int fei = indexOfLex(f, e, false);
    int fi = indexOfFLex(f, false);
    if (fei < 0 || fi < 0)
      return 0.0;
    return feLexCounts.get(fei) * 1.0 / fLexCounts.get(fi);
  }

  @Override
  public String toString(AlignmentTemplateInstance phrase, boolean withAlignment) {
    return phrase.toString(withAlignment);
  }
}
