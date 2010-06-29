package edu.stanford.nlp.mt.train;

import java.util.*;

/**
 * The preferred way of instanciating PhraseExtractor is to extend AbstractPhraseExtractor.
 * Its constructor takes a list of AbstractFeatureExtractor as argument, which are then 
 * used in {@link #addPhraseToIndex(WordAlignment,int,int,int,int,boolean,float)} and
 * {@link #extractPhrasesFromAlGrid(WordAlignment)}, 
 * where each feature extractor is executed on earch phrase pair.
 *
 * @author Michel Galley
 */ 
public abstract class AbstractPhraseExtractor implements PhraseExtractor {

  // Two thresholds on phrase lengths:
  // maxELen: only phrase pairs of length <= maxELen are constructed;
  // maxLen:  only phrase pairs of length <= maxLen are stored into
  // memory, and have their features printed at the end. Having different
  // thresholds is useful for cases where, e.g., you want to build
  // reordering models for phrase pairs shorter than 7 words, but need
  // contextual phrases larger than 7 to compute these features.

  static public final String MAX_PHRASE_LEN_OPT = "maxLen";
  static public final String MAX_PHRASE_LEN_E_OPT = "maxLenE";
  static public final String MAX_PHRASE_LEN_F_OPT = "maxLenF";
  static public final String MAX_EXTRACTED_PHRASE_LEN_OPT = "maxELen";
  static public final String MAX_EXTRACTED_PHRASE_LEN_E_OPT = "maxELenE";
  static public final String MAX_EXTRACTED_PHRASE_LEN_F_OPT = "maxELenF";
  static public final String ONLY_TIGHT_PHRASES_OPT = "onlyTightPhrases";
  static public final String ONLY_TIGHT_DTUS_OPT  = "onlyTightDTUs";

  static public final int DEFAULT_MAX_LEN = 7;
  static public final int DEFAULT_MAX_LEN_HIER = 500;
  static public final int DEFAULT_MAX_E_LEN = 7;

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugAbstractPhraseExtractor";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  public static final String PRINT_GRID_PROPERTY = "PrintGridWithMaxLen";
  public static final int PRINT_GRID_MAX_LEN = Integer.parseInt(System.getProperty(PRINT_GRID_PROPERTY, "-1"));

  public static final String MAX_SENT_LEN_PROPERTY = "maxLen";
  public static final int MAX_SENT_LEN = Integer.parseInt(System.getProperty(MAX_SENT_LEN_PROPERTY, "256"));

  public static final String PRINT_PHRASAL_GRID_PROPERTY = "PrintPhrasalGrid";
  public static final boolean PRINT_PHRASAL_GRID = Boolean.parseBoolean(System.getProperty(PRINT_PHRASAL_GRID_PROPERTY, "false"));

  public static final String NO_EMPTY_ALIGNMENT_PROPERTY = "NoEmptyAlignmentPhrases";
  public static final boolean NO_EMPTY_ALIGNMENT = Boolean.parseBoolean(System.getProperty(NO_EMPTY_ALIGNMENT_PROPERTY, "true"));

  static int maxPhraseLenF = DEFAULT_MAX_LEN, maxPhraseLenE = DEFAULT_MAX_LEN;
  static int maxExtractedPhraseLenF = DEFAULT_MAX_E_LEN, maxExtractedPhraseLenE = DEFAULT_MAX_E_LEN;

  static boolean onlyTightPhrases = false, onlyTightDTUs = false;

  final boolean extractBoundaryPhrases;
  final List<AbstractFeatureExtractor> extractors;
  final AlignmentTemplates alTemps;
  AlignmentTemplateInstance alTemp;
  DTUInstance dtuTemp;
  AlignmentGrid alGrid;

  protected AbstractPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {

    System.err.println("AbstractPhraseExtractor: "+maxPhraseLenF);
    this.alTemps = alTemps;
    this.extractors = extractors;
    this.alTemp = new AlignmentTemplateInstance();
    this.dtuTemp = new DTUInstance();
    this.alGrid = new AlignmentGrid(0,0);

    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(SymmetricalWordAlignment.ADD_BOUNDARY_MARKERS_OPT,"false"));
    boolean unalignedBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(SymmetricalWordAlignment.UNALIGN_BOUNDARY_MARKERS_OPT,"false"));
    extractBoundaryPhrases = (addBoundaryMarkers && unalignedBoundaryMarkers);
  }

  protected boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    return false;
  }

  protected void addPhraseToIndex
      (WordAlignment sent, int f1, int f2, int e1, int e2, boolean isConsistent, float weight) {

    if (onlyTightPhrases) {
      if (sent.f2e(f1).size() == 0 || sent.f2e(f2).size() == 0 || sent.e2f(e1).size() == 0 || sent.e2f(e2).size() == 0)
        return;
    }

    if (ignore(sent, f1, f2, e1, e2))
      return;

    // Check if alTemp meets length requirements:
    if (f2-f1>=maxExtractedPhraseLenF || e2-e1>=maxExtractedPhraseLenE) {
      if (isConsistent) {
        alGrid.addAlTemp(f1,f2,e1,e2);
      }
      if(DETAILED_DEBUG)
        System.err.printf("skipping too long: %d %d\n",f2-f1+1,e2-e1+1);
      return;
    }

    // Create alTemp:
    AlignmentTemplateInstance alTemp;
    alTemp = new AlignmentTemplateInstance(sent,f1,f2,e1,e2,weight);
    alGrid.addAlTemp(alTemp, isConsistent);

    alTemps.addToIndex(alTemp);
    alTemps.incrementAlignmentCount(alTemp);
  }

  // For DTU phrase extraction:
  protected AlignmentTemplateInstance addPhraseToIndex
      (WordAlignment sent, BitSet fs, BitSet es, boolean fContiguous,
       boolean eContiguous, boolean isConsistent, boolean ignoreContiguous) {

    if (ignoreContiguous)
      if (fContiguous && eContiguous)
        return null;

    if (onlyTightPhrases || (onlyTightDTUs && (!fContiguous || !eContiguous))) {
      int f1 = fs.nextSetBit(0);
      int f2 = fs.length()-1;
      int e1 = es.nextSetBit(0);
      int e2 = es.length()-1;
      if (sent.f2e(f1).size() == 0 || sent.f2e(f2).size() == 0 || sent.e2f(e1).size() == 0 || sent.e2f(e2).size() == 0)
        return null;
    }

    // Check if dtuTemp meets length requirements:
    if (fs.cardinality() > maxExtractedPhraseLenF || es.cardinality() > maxExtractedPhraseLenE) {
      if (isConsistent && fContiguous && eContiguous) {
        alGrid.addAlTemp(fs.nextSetBit(0), fs.length()-1, es.nextSetBit(0), es.length()-1);
      }
      if (DETAILED_DEBUG)
        System.err.printf("skipping too long: %d %d\n",fs.cardinality(),es.cardinality());
      return null;
    }

    // Create dtuTemp:
    DTUInstance dtuTemp = new DTUInstance(sent, fs, es, fContiguous, eContiguous);
    alGrid.addAlTemp(dtuTemp, isConsistent);

    alTemps.addToIndex(dtuTemp);
    alTemps.incrementAlignmentCount(dtuTemp);

    return dtuTemp;
  }

  protected void extractPhrasesFromGrid(WordAlignment sent) {
    int fsize = sent.f().size();
    int esize = sent.e().size();
    // Features are extracted only once all phrases for a given
    // sentence pair are in memory
    for (AbstractFeatureExtractor e : extractors) {
      for (AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
        e.extract(alTemp, alGrid);
        if (PRINT_PHRASAL_GRID && fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
          alGrid.printAlTempInGrid
            ("phrase id: "+alTemp.getKey(),alTemp,System.err);
      }
    }
  }

  @SuppressWarnings("unused")
  private boolean checkAlignmentConsistency(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean aligned = false;
    if (f2-f1 > maxPhraseLenF) return false;
    if (e2-e1 > maxPhraseLenE) return false;
    for (int fi=f1; fi<=f2; ++fi)
      for (int ei : sent.f2e(fi)) {
        if (!(e1 <= ei && ei <= e2))
          return false;
        aligned = true;
      }
    if (!aligned) return false;
    for (int ei=e1; ei<=e2; ++ei)
      for (int fi : sent.e2f(ei))
        if (!(f1 <= fi && fi <= f2))
          return false;
    return true;
  }

  static void setPhraseExtractionProperties(Properties prop) {

    if (Boolean.parseBoolean(prop.getProperty(PhraseExtract.LEX_REORDERING_HIER_OPT, "false"))) {
      prop.setProperty(PhraseExtract.LEX_REORDERING_PHRASAL_OPT,"true");
      maxPhraseLenF = DEFAULT_MAX_LEN_HIER; 
      maxPhraseLenE = DEFAULT_MAX_LEN_HIER;
    }

    int max = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_OPT,"-1"));
    int maxF = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_F_OPT,"-1"));
    int maxE = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_E_OPT,"-1"));

    int max2 = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_OPT,"-1"));
    int max2F = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_F_OPT,"-1"));
    int max2E = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_E_OPT,"-1"));

    if (max > 0) {
      System.err.printf("changing default max phrase length: %d -> %d\n", maxPhraseLenF, max);
      maxPhraseLenF = maxPhraseLenE = max;
      if (max2 < 0)
        max2 = max;
    }
    if (maxF > 0) {
      System.err.printf("changing default max phrase length (F): %d -> %d\n", maxPhraseLenF, maxF);
      maxPhraseLenF = maxF;
    }
    if (maxE > 0) {
      System.err.printf("changing default max phrase length (E): %d -> %d\n", maxPhraseLenE, maxE);
      maxPhraseLenE = maxE;
    }

    if (max2 > 0) {
      System.err.printf("changing default max extracted phrase length: %d -> %d\n", maxExtractedPhraseLenF, max2);
      maxExtractedPhraseLenF = maxExtractedPhraseLenE = max2;
    }
    if (max2F > 0) {
      System.err.printf("changing default max extracted phrase length (F): %d -> %d\n", maxExtractedPhraseLenF, max2F);
      maxExtractedPhraseLenF = max2F;
    }
    if (max2E > 0) {
      System.err.printf("changing default max extracted phrase length (E): %d -> %d\n", maxExtractedPhraseLenE, max2E);
      maxExtractedPhraseLenE = max2E;
    }
    System.err.printf("maximum phrase length (F): %d\n", maxPhraseLenF);
    System.err.printf("maximum phrase length (E): %d\n", maxPhraseLenE);
    System.err.printf("maximum extracted phrase length (F): %d\n", maxExtractedPhraseLenF);
    System.err.printf("maximum extracted phrase length (E): %d\n", maxExtractedPhraseLenE);

    assert (maxPhraseLenE >= maxExtractedPhraseLenE);
    assert (maxPhraseLenF >= maxExtractedPhraseLenF);

    String optStr = prop.getProperty(ONLY_TIGHT_PHRASES_OPT);
    onlyTightPhrases = optStr != null && !optStr.equals("false");

    optStr = prop.getProperty(ONLY_TIGHT_DTUS_OPT);
    onlyTightDTUs = optStr != null && !optStr.equals("false");

    DTUPhraseExtractor.setDTUExtractionProperties(prop);
  }

  AlignmentGrid getAlGrid() { return alGrid; }

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {}

  public Object clone() throws CloneNotSupportedException {
    AbstractPhraseExtractor c = (AbstractPhraseExtractor) super.clone();
    c.alGrid = new AlignmentGrid(0,0);
    c.alTemp = new AlignmentTemplateInstance();
    c.dtuTemp = new DTUInstance();
    return c;
  }

}
