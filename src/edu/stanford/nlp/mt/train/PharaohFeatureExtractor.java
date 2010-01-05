package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;

import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Extractor for the five base feature functions of Moses/Pharaoh 
 * (four translation probabilities plus phrase penalty).
 * 
 * @author Michel Galley
 */
public class PharaohFeatureExtractor extends AbstractFeatureExtractor {

  public static final double MIN_WSUM = 1e-4;

  public static final String DEBUG_PROPERTY = "DebugPharaohFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  public static final String PRINT_COUNTS_PROPERTY = "DebugPrintCounts";
  public static final boolean PRINT_COUNTS = Boolean.parseBoolean(System.getProperty(PRINT_COUNTS_PROPERTY, "false"));

  public static final String STORE_PROPERTY = "Store";
  public static final boolean STORE = Boolean.parseBoolean(System.getProperty(STORE_PROPERTY, "true"));

  public static final String FILL_HASH_PROPERTY = "FillHash";
  public static final boolean FILL_HASH = Boolean.parseBoolean(System.getProperty(FILL_HASH_PROPERTY, "true"));

  double phiFilter = 0.0, lexFilter = 0.0;
  boolean ibmLexModel = false, onlyML = false;
  int numPasses = 1; 

  final DynamicIntegerArrayIndex lexIndex = new DynamicIntegerArrayIndex();
  final Index<Integer> fLexIndex = new HashIndex<Integer>(), eLexIndex = new HashIndex<Integer>();

  IntArrayList feCounts = new IntArrayList();
  IntArrayList fCounts = new IntArrayList();
  IntArrayList eCounts = new IntArrayList();

  IntArrayList feLexCounts = new IntArrayList();
  IntArrayList fLexCounts = new IntArrayList();
  IntArrayList eLexCounts = new IntArrayList();

  public static final IString NULL_STR = new IString("NULL");

  @Override
	public void init(Properties prop, Index<String> featureIndex, AlignmentTemplates alTemps) {
    super.init(prop, featureIndex, alTemps);
    // Set counts of "NULL":
    fLexCounts.add(0);
    eLexCounts.add(0);
    // Do we want exact counts?
    boolean exact = prop.getProperty
      (CombinedFeatureExtractor.EXACT_PHI_OPT,"true").equals("true");
    this.numPasses = exact ? 2 : 1;
    System.err.println("Exact denominator counts for phi(f|e): "+exact);
    // IBM lexicalized model?
    ibmLexModel = prop.getProperty
      (CombinedFeatureExtractor.IBM_LEX_MODEL_OPT,"false").equals("true");
    if(!ibmLexModel)
      alTemps.enableAlignmentCounts(true);
    onlyML = prop.getProperty
      (CombinedFeatureExtractor.ONLY_ML_OPT,"false").equals("true");
    // Filtering:
    phiFilter = Double.parseDouble
      (prop.getProperty(CombinedFeatureExtractor.PTABLE_PHI_FILTER_OPT,"-1e30"));
    lexFilter = Double.parseDouble
      (prop.getProperty(CombinedFeatureExtractor.PTABLE_LEX_FILTER_OPT,"-1e30"));
    System.err.printf("minimum phi(e|f) = %.5f\n", phiFilter);
    System.err.printf("minimum lex(e|f) = %.5f\n", lexFilter);
  }

  @Override
	public int getRequiredPassNumber() { return numPasses; }

  @Override
	public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {
    // Increment word counts:
    Sequence<IString> f = sent.f();
    Sequence<IString> e = sent.e();
    for(int fi=0; fi<f.size(); ++fi) {
      // Word pair counts (f,e):
      for(int ei : sent.f2e(fi))
        addLexCount(f.get(fi),e.get(ei));
      // Unaligned foreign words (f,NULL):
      if(sent.f2e(fi).size() == 0)
        addLexCount(f.get(fi),NULL_STR);
    }
    // Unaligned English words (NULL,e):
    for(int ei=0; ei<e.size(); ++ei)
      if(sent.e2f(ei).size() == 0)
        addLexCount(NULL_STR,e.get(ei));
  }

  @Override
	public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    // Code below will only get executed during the last pass:
    if(getCurrentPass()+1 == getRequiredPassNumber()) {
      if(DEBUG_LEVEL >= 2)
        System.err.println("Adding phrase to table: "+alTemp.f().toString(" ")+" -> "+alTemp.e().toString(" "));
      // Increment phrase counts c(f,e), c(f), c(e):
      if(FILL_HASH) {
        addCountToArray(feCounts, alTemp.getKey());
        addCountToArray(fCounts, alTemp.getFKey());
        addCountToArray(eCounts, alTemp.getEKey());
      }
      if(DEBUG_LEVEL >= 2)
        System.err.printf("Assigned IDs: key=%d fKey=%d eKey=%d\n",alTemp.getKey(), alTemp.getFKey(), alTemp.getEKey());
    }
  }

  /**
   * Print the five translation model features that appear in Moses' phrase tables.
   */
  @Override
	public Object score(AlignmentTemplate alTemp) {
    // print phi(f|e), lex(f|e), phi(e|f), lex(e|f), and phrase penalty:
    int idx = alTemp.getKey();
    int idxF = alTemp.getFKey();
    int idxE = alTemp.getEKey();
    if(idx >= feCounts.size() || idxF >= fCounts.size() || idxE >= eCounts.size()) {
      System.err.println("can't get Pharaoh translation features for phrase: "+alTemp.toString(true));
      return null;
    }
    assert(idx >= 0 && idxF >= 0 && idxE >= 0);
    assert(idx < feCounts.size());
    // Compute phi features p(f|e) and p(e|f):
    double phi_f_e = feCounts.get(idx)*1.0/eCounts.get(idxE);
    double phi_e_f = feCounts.get(idx)*1.0/fCounts.get(idxF);
    if(phiFilter > phi_e_f)
      return null;
    // Compute lexical weighting features:
    double lex_f_e;
    double lex_e_f;
    if(ibmLexModel) {
      lex_f_e = getIBMLexScore(alTemp);
      lex_e_f = getIBMLexScoreInv(alTemp);
    } else {
      lex_f_e = getLexScore(alTemp);
      lex_e_f = getLexScoreInv(alTemp);
    }
    // Set phrase penalty:
    double phrasePen = 2.718;
    // Determine if need to filter phrase:
    if(lexFilter > lex_e_f)
      return null;
    if(PRINT_COUNTS) {
      // -- Additional info for debugging purposes:
      return new double[] { 
        phi_f_e, lex_f_e, phi_e_f, lex_e_f, phrasePen,
        feCounts.get(idx), eCounts.get(idxE), fCounts.get(idxF) };
    } else if(onlyML) {
      // -- only two features: relative freq. in both directions:
      return new double[] { phi_f_e, phi_e_f };
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
        if(els.length != 5) {
          System.err.println(Arrays.toString(els));
          throw new RuntimeException("Expecting five fields in phrase table, found: "+els.length);
        }
        String oldStr = els[0]+" ||| "+els[1]+" ||| "+els[2]+" ||| "+els[3];
        AlignmentTemplate alTemp = new AlignmentTemplate(els[0], els[1], els[2], false);
        assert(alTemp.toString().equals(oldStr));
        alTemps.addToIndex(alTemp);
        // Get our scores for it:
        double[] ourScores = (double[]) score(alTemp);
        // Compare them to scores in the file:
        String[] mosesScores = els[4].split("\\s+");
        for(int i=0; i<mosesScores.length; ++i) {
          double mosesScore = Double.parseDouble(mosesScores[i]);
          double error = 1-mosesScore/ourScores[i];
          if(Math.abs(error) > 1e-2) {
            System.err.printf("Different score for feature %d : %.3f != %.3f\n", i, mosesScore, ourScores[i]);
            System.err.println("Phrase from Moses phrase table: "+oldStr);
            System.err.println("Phrase from our model: "+alTemp.toString());
            System.err.println("Features from Moses phrase table: "+els[4]);
            System.err.println("Features from our computation: "+Arrays.toString(ourScores));
          } else {
            if(DEBUG_LEVEL >= 2)
              System.err.printf("Same score for feature %d : %.3f\n", i, mosesScore);
          }
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void addLexCount(IString f, IString e) {
    if(DEBUG_LEVEL >= 2)
      System.err.println("Adding lexical alignment count: c(f = "+f+"("+f.getId()+"), e="+e+" ("+e.getId()+"))");
    if(FILL_HASH) {
      // Increment word counts for lexical weighting:
      addCountToArray(feLexCounts, indexOfLex(f,e,true));
      addCountToArray(fLexCounts, indexOfFLex(f,true));
      addCountToArray(eLexCounts, indexOfELex(e,true));
    }
  }

  private int indexOfLex(IString f, IString e, boolean add)
  { return lexIndex.indexOf(new int[] {f.getId(), e.getId()}, add); }

  private int indexOfFLex(IString f, boolean add)
  { synchronized(fLexIndex) { return fLexIndex.indexOf(f.getId(), add); } }

  private int indexOfELex(IString e, boolean add)
  { synchronized(eLexIndex) { return eLexIndex.indexOf(e.getId(), add); } }

  private static void addCountToArray(IntArrayList list, int idx) {
    if(idx < 0)
      return;
    if(STORE) {
      synchronized(list) {
        while(idx >= list.size())
          list.add(0);
        int newCount = list.get(idx)+1;
        list.set(idx,newCount);
      }
      if(DEBUG_LEVEL >= 3)
        System.err.println("Increasing count idx="+idx+" in vector ("+list+").");
    }
  }

  /**
   * Lexically-weighted probability of alTemp.f() given alTemp.e() according to Moses.
   */
  private double getLexScore(AlignmentTemplate alTemp) {
    if(DEBUG_LEVEL >= 1)
      System.err.println("Computing p(f|e) for alignment template: "+alTemp.toString(false));
    // Each French word must be explained:
    double lex = 1.0;
    for(int fi=0; fi<alTemp.f().size();++fi) {
      if(alTemp.f().get(fi).equals(DTUPhraseExtractor.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.f2e(fi).size();
      if(alCount == 0) {
        wSum = getLexProb(alTemp.f().get(fi),NULL_STR);
      } else {
        for(int ei : alTemp.f2e(fi)) {
          wSum += getLexProb(alTemp.f().get(fi),alTemp.e().get(ei));
        }
        wSum /= alCount;
      }
      if(DEBUG_LEVEL >= 1 || wSum == 0.0) {
        System.err.printf("w(%s|...) = %.3f\n",alTemp.f().get(fi),wSum);
        if(wSum == 0)
          System.err.println("  WARNING: wsum = "+wSum);
      }
      if(wSum == 0)
        wSum = MIN_WSUM;
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Lexically-weighted probability of alTemp.e() given alTemp.f() according to Moses.
   */
  private double getLexScoreInv(AlignmentTemplate alTemp) {
    if(DEBUG_LEVEL >= 1)
      System.err.println("Computing p(e|f) for alignment template: "+alTemp.toString());
    // Each English word must be explained:
    double lex = 1.0;
    for(int ei=0; ei<alTemp.e().size();++ei) {
      if(alTemp.e().get(ei).equals(DTUPhraseExtractor.GAP_STR))
        continue;
      double wSum = 0.0;
      int alCount = alTemp.e2f(ei).size();
      if(alCount == 0) {
        wSum += getLexProbInv(NULL_STR,alTemp.e().get(ei));
      } else {
        for(int fi : alTemp.e2f(ei)) {
          wSum += getLexProbInv(alTemp.f().get(fi),alTemp.e().get(ei));
        }
        wSum /= alCount;
      }
      if(DEBUG_LEVEL >= 1 || wSum == 0.0)
        System.err.printf("w(%s|...) = %.3f\n",alTemp.e().get(ei),wSum);
      if(wSum == 0)
        wSum = MIN_WSUM;
      lex *= wSum;
    }
    return lex;
  }
  
  /**
   * Lexically-weighted probability of alTemp.f() given alTemp.e() according to IBM.
   * In the IBM model, the sum is over all word pairs. It is generally not as good as 
   * as the IBM version, but this version requires much less memory.
   */
  private double getIBMLexScore(AlignmentTemplate alTemp) {
    if(DEBUG_LEVEL >= 1)
      System.err.println("Computing p(f|e) for alignment template: "+alTemp.toString());
    // Each French word must be explained:
    double lex = 1.0;
    for(int fi=0; fi<alTemp.f().size();++fi) {
      double wSum = 0.0;
      for(int ei=0; ei<alTemp.e().size();++ei) {
        wSum += getLexProb(alTemp.f().get(fi),alTemp.e().get(ei));
      }
      wSum /= alTemp.e().size();
      if(DEBUG_LEVEL >= 1)
        System.err.printf("w(%s|...) = %.3f\n",alTemp.f().get(fi),wSum);
      assert(wSum > 0);
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Lexically-weighted probability of alTemp.e() given alTemp.f() according to IBM.
   * In the IBM model, the sum is over all word pairs. It is generally not as good as 
   * as the IBM version, but this version requires much less memory.
   */
  private double getIBMLexScoreInv(AlignmentTemplate alTemp) {
    if(DEBUG_LEVEL >= 1)
      System.err.println("Computing p(e|f) for alignment template: "+alTemp.toString());
    // Each French word must be explained:
    double lex = 1.0;
    for(int ei=0; ei<alTemp.e().size();++ei) {
      double wSum = 0.0;
      for(int fi=0; fi<alTemp.f().size();++fi) {
        wSum += getLexProbInv(alTemp.f().get(fi),alTemp.e().get(ei));
      }
      wSum /= alTemp.f().size();
      if(DEBUG_LEVEL >= 1)
        System.err.printf("w(%s|...) = %.3f\n",alTemp.e().get(ei),wSum);
      assert(wSum > 0);
      lex *= wSum;
    }
    return lex;
  }

  /**
   * Word translation probability of f given e. Note that getLexProb(f,e)
   * generally does not return the same as getLexProbInv(e,f), since 
   * normalization counts are different.
   */
  private double getLexProb(IString f, IString e) {
    if(DEBUG_LEVEL >= 1) {
      System.err.print("p(f = \""+f+"\" | e = \""+e+"\") = ");
      System.err.print(feLexCounts.get(indexOfLex(f,e,false)));
      System.err.print("/");
      System.err.println(eLexCounts.get(indexOfELex(e,false)));
    }
    int fei = indexOfLex(f,e,false);
    int ei = indexOfELex(e,false);
    if(fei < 0 || ei < 0) return 0.0;
    return feLexCounts.get(fei)*1.0/eLexCounts.get(ei);
  }
  
  /**
   * Word translation probability of e given f. Note that getLexProb(f,e)
   * generally does not return the same as getLexProbInv(e,f), since 
   * normalization counts are different.
   */
  private double getLexProbInv(IString f, IString e) {
    if(DEBUG_LEVEL >= 1) {
      System.err.print("p(e = \""+e+"\" | f = \""+f+"\") = ");
      System.err.print(feLexCounts.get(indexOfLex(f,e,false)));
      System.err.print("/");
      System.err.println(fLexCounts.get(indexOfFLex(f,false)));
    }
    int fei = indexOfLex(f,e,false);
    int fi = indexOfFLex(f,false);
    if(fei < 0 || fi < 0) return 0.0;
    return feLexCounts.get(fei)*1.0/fLexCounts.get(fi);
  }
}
