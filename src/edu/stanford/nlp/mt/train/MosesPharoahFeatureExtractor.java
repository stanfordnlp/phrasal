package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.DTUTable;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;

import it.unimi.dsi.fastutil.ints.AbstractIntList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Extractor for the five base feature functions of Moses/Pharaoh (four
 * translation probabilities plus phrase penalty).
 * 
 * @author Michel Galley
 */
public class MosesPharoahFeatureExtractor extends AbstractFeatureExtractor implements
    PhrasePrinter {

  public static final double MIN_LEX_PROB = 1e-5;

  public static final double DEFAULT_PHI_FILTER = 1e-4;
  public static final double DEFAULT_LEX_FILTER = 0;

  public static final String DEBUG_PROPERTY = "DebugMosesFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(
      DEBUG_PROPERTY, "0"));

  public static final String PRINT_COUNTS_PROPERTY = "DebugPrintCounts";
  public static final boolean PRINT_COUNTS = Boolean.parseBoolean(System
      .getProperty(PRINT_COUNTS_PROPERTY, "false"));
  protected double phiFilter = DEFAULT_PHI_FILTER,
      lexFilter = DEFAULT_LEX_FILTER;
  protected boolean ibmLexModel = false, onlyPhi = false;
  protected boolean usePmi = false;
  protected boolean doLog = true;
  protected int numPasses = 1;

  protected final DynamicIntegerArrayIndex lexIndex = new DynamicIntegerArrayIndex();
  protected final Index<Integer> fLexIndex = new HashIndex<Integer>(),
      eLexIndex = new HashIndex<Integer>();

  protected final IntArrayList feCounts = new IntArrayList();
  protected final IntArrayList fCounts = new IntArrayList();
  protected final IntArrayList eCounts = new IntArrayList();
  protected List<Double> totalCounts = new ArrayList<Double>(0);
  protected double totalFECount = -1;
  protected double totalFCount = -1;
  protected double totalECount = -1;
  protected List<Double> totalLexCounts = new ArrayList<Double>(0);
  protected double totalFELexCount = -1;
  protected double totalFLexCount = -1;
  protected double totalELexCount = -1;

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
    boolean exact = prop.getProperty(PhraseExtract.EXACT_PHI_OPT, "true")
        .equals("true");
    this.numPasses = exact ? 2 : 1;
    System.err.println("Exact denominator counts for phi(f|e): " + exact);
    // IBM lexicalized model?
    ibmLexModel = prop.getProperty(PhraseExtract.IBM_LEX_MODEL_OPT, "false")
        .equals("true");
    if (!ibmLexModel)
      alTemps.enableAlignmentCounts(true);
    onlyPhi = prop.getProperty(PhraseExtract.ONLY_ML_OPT, "false").equals(
        "true");
    usePmi = prop.getProperty(PhraseExtract.USE_PMI, "false").equals(
        "true");
    doLog = prop.getProperty(PhraseExtract.DO_LOG_PHAROAH_SCORES, "true").equals(
        "true");
    // Filtering:
    phiFilter = Double.parseDouble(prop.getProperty(
        PhraseExtract.PTABLE_PHI_FILTER_OPT,
        Double.toString(DEFAULT_PHI_FILTER)));
    lexFilter = Double.parseDouble(prop.getProperty(
        PhraseExtract.PTABLE_LEX_FILTER_OPT,
        Double.toString(DEFAULT_LEX_FILTER)));
    System.err.printf("Cut-off value for phi(e|f): %.5f\n", phiFilter);
    System.err.printf("Cut-off value for lex(e|f): %.5f\n", lexFilter);
  }

  @Override
  public int getRequiredPassNumber() {
    return numPasses;
  }

  @Override
  public void featurizeSentence(SymmetricalWordAlignment sent, String info,
      AlignmentGrid alGrid) {
    // Increment word counts:
    Sequence<IString> f = sent.f();
    Sequence<IString> e = sent.e();
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
    // Code below will only get executed during the last pass:
    if (getCurrentPass() + 1 == getRequiredPassNumber()) {
      if (DEBUG_LEVEL >= 2)
        System.err.println("Adding phrase to table: "
            + alTemp.f().toString(" ") + " -> " + alTemp.e().toString(" "));
      // Increment phrase counts c(f,e), c(f), c(e):
      addCountToArray(feCounts, alTemp.getKey());
      addCountToArray(fCounts, alTemp.getFKey());
      addCountToArray(eCounts, alTemp.getEKey());
      if (DEBUG_LEVEL >= 2)
        System.err.printf("Assigned IDs: key=%d fKey=%d eKey=%d\n",
            alTemp.getKey(), alTemp.getFKey(), alTemp.getEKey());
    }
  }

  /**
   * Print the five translation model features that appear in Moses' phrase
   * tables.
   */
  @Override
  public Object score(AlignmentTemplate alTemp) {
    // print phi(f|e), lex(f|e), phi(e|f), lex(e|f), and phrase penalty:
    if (usePmi) {
      synchronized (totalCounts) {
        if (totalCounts.size() == 0){
          totalCounts.add(0.0);
          // initialize the sum of all counts to calculate PMI
          totalFECount = 0;
          totalFCount = 0;
          totalECount = 0;
          for (int i : feCounts.elements())
             totalFECount += i;
          for (int i : fCounts.elements())
             totalFCount += i;
          for (int i : eCounts.elements())
             totalECount += i;
        }
      }

      synchronized (totalLexCounts) {
        if (totalLexCounts.size() == 0){
          totalLexCounts.add(0.0);
          // initialize the sum of all counts to calculate PMI
          totalFELexCount = 0;
          totalFLexCount = 0;
          totalELexCount = 0;
          for (int i : feLexCounts.elements())
             totalFELexCount += i;
          for (int i : fLexCounts.elements())
             totalFLexCount += i;
          for (int i : eLexCounts.elements())
             totalELexCount += i;
        }
      }
    }

    int idx = alTemp.getKey();
    int idxF = alTemp.getFKey();
    int idxE = alTemp.getEKey();
    if (idx >= feCounts.size() || idxF >= fCounts.size()
        || idxE >= eCounts.size()) {
      System.err.println("can't get Pharaoh translation features for phrase: "
          + alTemp.toString(true));
      return null;
    }
    assert (idx >= 0 && idxF >= 0 && idxE >= 0);
    assert (idx < feCounts.size());
    // Compute phi features p(f|e) and p(e|f):
    double pairCount = feCounts.get(idx); 
    double eCount = eCounts.get(idxE);
    double fCount = fCounts.get(idxF);
    double phi_f_e = pairCount * 1.0 / eCount;
    double phi_e_f = pairCount * 1.0 / fCount;

    if (phiFilter > phi_e_f)
      return null;
    double pTF = pairCount/totalFECount;
    double unnormalized_pmi = Math.log(pTF / ((eCount/totalECount) * (fCount/totalFCount)));
    double pmi = unnormalized_pmi / (-1 * Math.log(pTF));
    // Compute lexical weighting features:
    double lex_f_e;
    double lex_e_f;
    if (ibmLexModel) {
      lex_f_e = getIBMLexScore(alTemp);
      lex_e_f = getIBMLexScoreInv(alTemp);
    } else {
      lex_f_e = getLexScore(alTemp);
      lex_e_f = getLexScoreInv(alTemp);
    }
    double lexPMI = getLexPmiScore(alTemp, true);
    // Set phrase penalty:
    double phrasePen = 2.718;
    // Determine if need to filter phrase:
    if (lexFilter > lex_e_f)
      return null;

    if (doLog) {
      phi_f_e = Math.log(phi_f_e);
      phi_e_f = Math.log(phi_e_f);
      lex_f_e = Math.log(lex_f_e);
      lex_e_f = Math.log(lex_e_f);
    }
    if (PRINT_COUNTS) {
      // -- Additional info for debugging purposes:
      return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f, phrasePen,
          feCounts.get(idx), eCounts.get(idxE), fCounts.get(idxF) };
    } else if (onlyPhi) {
      // -- only two features: relative freq. in both directions:
      return new double[] { phi_f_e, phi_e_f };
    } else if (usePmi) {
      return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f, phrasePen, pmi, lexPMI };
    } else {
      // -- 5 basic features functions of Moses:
      return new double[] { phi_f_e, lex_f_e, phi_e_f, lex_e_f, phrasePen };
    }
  }

  /**
   * Check features against those read from Moses phrase table.
   */
  public void checkAgainst(BufferedReader ref) {
    try {
      for (String fLine; (fLine = ref.readLine()) != null;) {
        // Read alignment template from file:
        String[] els = fLine.split("\\s+\\|\\|\\|\\s+");
        if (els.length != 5) {
          System.err.println(Arrays.toString(els));
          throw new RuntimeException(
              "Expecting five fields in phrase table, found: " + els.length);
        }
        String oldStr = els[0] + " ||| " + els[1] + " ||| " + els[2] + " ||| "
            + els[3];
        AlignmentTemplate alTemp = new AlignmentTemplate(els[0], els[1],
            els[2], false);
        assert (alTemp.toString().equals(oldStr));
        alTemps.addToIndex(alTemp);
        // Get our scores for it:
        double[] ourScores = (double[]) score(alTemp);
        // Compare them to scores in the file:
        String[] mosesScores = els[4].split("\\s+");
        for (int i = 0; i < mosesScores.length; ++i) {
          double mosesScore = Double.parseDouble(mosesScores[i]);
          double error = 1 - mosesScore / ourScores[i];
          if (Math.abs(error) > 1e-2) {
            System.err.printf(
                "Different score for feature %d : %.3f != %.3f\n", i,
                mosesScore, ourScores[i]);
            System.err.println("Phrase from Moses phrase table: " + oldStr);
            System.err.println("Phrase from our model: " + alTemp.toString());
            System.err.println("Features from Moses phrase table: " + els[4]);
            System.err.println("Features from our computation: "
                + Arrays.toString(ourScores));
          } else {
            if (DEBUG_LEVEL >= 2)
              System.err.printf("Same score for feature %d : %.3f\n", i,
                  mosesScore);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
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
   * Lexically-weighted PMI as the sum of alTemp.f() given alTemp.e() 
   * word-level PMI normalized by the number of words in alTemp.f()
   */
  private double getLexPmiScore(AlignmentTemplate alTemp, boolean doNormalize) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing word-level PMI(f|e) for alignment template: "
          + alTemp.toString(true));
    // PMI calculation only accounts for French words that are aligned,
    // normalized by the number of french words
    double pmiSum = 1.0;
    for (int fi = 0; fi < alTemp.f().size(); ++fi) {
      if (alTemp.f().get(fi).equals(DTUTable.GAP_STR))
        continue;
      double wPMI = 0.0;
      int alCount = alTemp.f2e(fi).size();
      if (alCount != 0) {
        for (int ei : alTemp.f2e(fi)) {
          assert (!alTemp.e().get(ei).equals(DTUTable.GAP_STR));
          wPMI += getLexPmi(alTemp.f().get(fi), alTemp.e().get(ei), doNormalize);
        }
        wPMI /= alCount;
      }
      pmiSum += wPMI;
    }
    //normalize the sum of pmi by the length of the French phrase
    double pmiAvg = pmiSum / alTemp.f().size();
    return pmiAvg;
  }


  /**
   * Lexically-weighted probability of alTemp.f() given alTemp.e() according to
   * Moses.
   */
  private double getLexScore(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(f|e) for alignment template: "
          + alTemp.toString(true));
    // Each French word must be explained:
    double lex = 1.0;
    for (int fi = 0; fi < alTemp.f().size(); ++fi) {
      if (alTemp.f().get(fi).equals(DTUTable.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.f2e(fi).size();
      if (alCount == 0) {
        wSum = getLexProb(alTemp.f().get(fi), NULL_STR);
      } else {
        for (int ei : alTemp.f2e(fi)) {
          assert (!alTemp.e().get(ei).equals(DTUTable.GAP_STR));
          wSum += getLexProb(alTemp.f().get(fi), alTemp.e().get(ei));
        }
        wSum /= alCount;
      }
      if (DEBUG_LEVEL >= 1 || wSum == 0.0) {
        System.err.printf("w(%s|...) = %.3f\n", alTemp.f().get(fi), wSum);
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
   * Lexically-weighted probability of alTemp.e() given alTemp.f() according to
   * Moses.
   */
  private double getLexScoreInv(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(e|f) for alignment template: "
          + alTemp.toString());
    // Each English word must be explained:
    double lex = 1.0;
    for (int ei = 0; ei < alTemp.e().size(); ++ei) {
      if (alTemp.e().get(ei).equals(DTUTable.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.e2f(ei).size();
      if (alCount == 0) {
        wSum += getLexProbInv(NULL_STR, alTemp.e().get(ei));
      } else {
        for (int fi : alTemp.e2f(ei)) {
          wSum += getLexProbInv(alTemp.f().get(fi), alTemp.e().get(ei));
        }
        wSum /= alCount;
      }
      if (DEBUG_LEVEL >= 1 || wSum == 0.0)
        System.err.printf("w(%s|...) = %.3f\n", alTemp.e().get(ei), wSum);
      if (wSum == 0)
        wSum = MIN_LEX_PROB;
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Lexically-weighted probability of alTemp.f() given alTemp.e(). Alternative
   * to Moses' feature, which is similar to a feature in (Tillmann and Zhang,
   * 2006). getIBMLexScore (as opposed to getLexScore) does not require to
   * storage of any word alignment.
   */
  private double getIBMLexScore(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(f|e) for alignment template: "
          + alTemp.toString());
    // Each French word must be explained:
    double lex = 1.0;
    for (int fi = 0; fi < alTemp.f().size(); ++fi) {
      double wMax = MIN_LEX_PROB;
      int sz = alTemp.e().size();
      for (int ei = 0; ei < sz; ++ei) {
        double wCur = getLexProb(alTemp.f().get(fi), alTemp.e().get(ei));
        if (wCur > wMax)
          wMax = wCur;
      }
      double wNull = getLexProb(alTemp.f().get(fi), NULL_STR);
      if (wNull > wMax)
        wMax = wNull;
      if (DEBUG_LEVEL >= 1)
        System.err.printf("w(%s|...) = %.3f\n", alTemp.f().get(fi), wMax);
      assert (wMax > 0);
      lex *= wMax;
    }
    return lex;
  }

  /**
   * Lexically-weighted probability of alTemp.e() given alTemp.f(). Alternative
   * to Moses' feature, which is similar to a feature in (Tillmann and Zhang,
   * 2006). getIBMLexScoreInv (as opposed to getLexScoreInv) does not require to
   * storage of any word alignment.
   */
  private double getIBMLexScoreInv(AlignmentTemplate alTemp) {
    if (DEBUG_LEVEL >= 1)
      System.err.println("Computing p(e|f) for alignment template: "
          + alTemp.toString());
    // Each French word must be explained:
    double lex = 1.0;
    for (int ei = 0; ei < alTemp.e().size(); ++ei) {
      double wMax = MIN_LEX_PROB;
      int sz = alTemp.f().size();
      for (int fi = 0; fi < sz; ++fi) {
        double wCur = getLexProbInv(alTemp.f().get(fi), alTemp.e().get(ei));
        if (wCur > wMax)
          wMax = wCur;
      }
      double wNull = getLexProbInv(NULL_STR, alTemp.e().get(ei));
      if (wNull > wMax)
        wMax = wNull;
      if (DEBUG_LEVEL >= 1)
        System.err.printf("w(%s|...) = %.3f\n", alTemp.e().get(ei), wMax);
      lex *= wMax;
    }
    return lex;
  }

  /**
   * Point-wise Mutual Information of f and e. Note that getLexPmi(f,e)
   * should equal to getLexPmi(e,f)
   */
  private double getLexPmi(IString f, IString e, boolean doNormalize) {
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
    double pFE = feLexCounts.get(fei) * 1.0 / totalFELexCount;
    double pF = eLexCounts.get(ei) * 1.0 / totalELexCount;
    double pE = fLexCounts.get(fi) * 1.0 / totalFLexCount;
    double lexPMI = Math.log( pFE / (pF * pE));
    if (doNormalize)
      lexPMI = lexPMI / (-1 * Math.log(pFE));
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
