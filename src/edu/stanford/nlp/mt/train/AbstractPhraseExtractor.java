package edu.stanford.nlp.mt.train;

import java.util.*;

/**
 * The preferred way of instanciating PhraseExtractor is to extend
 * AbstractPhraseExtractor. Its constructor takes a list of
 * AbstractFeatureExtractor as argument, which are then used in
 * {@link #addPhraseToIndex(WordAlignment,int,int,int,int,boolean,float)}. where
 * each feature extractor is executed on earch phrase pair.
 * 
 * @author Michel Galley
 */
public abstract class AbstractPhraseExtractor implements PhraseExtractor {

  // Two thresholds on phrase lengths:
  // maxELen: only phrase pairs of length <= maxELen are constructed;
  // maxLen: only phrase pairs of length <= maxLen are stored into
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

  static public final int DEFAULT_MAX_LEN = 7;
  static public final int DEFAULT_MAX_LEN_HIER = 1000;
  static public final int DEFAULT_MAX_E_LEN = 7;

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugAbstractPhraseExtractor";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  public static final String PRINT_GRID_PROPERTY = "PrintGridWithMaxLen";
  public static final int PRINT_GRID_MAX_LEN = Integer.parseInt(System
      .getProperty(PRINT_GRID_PROPERTY, "-1"));

  public static final String MAX_SENT_LEN_PROPERTY = "maxLen";
  public static final int MAX_SENT_LEN = Integer.parseInt(System.getProperty(
      MAX_SENT_LEN_PROPERTY, "256"));

  public static final String PRINT_PHRASAL_GRID_PROPERTY = "PrintPhrasalGrid";
  public static final boolean PRINT_PHRASAL_GRID = Boolean.parseBoolean(System
      .getProperty(PRINT_PHRASAL_GRID_PROPERTY, "false"));

  public static final String NO_EMPTY_ALIGNMENT_PROPERTY = "NoEmptyAlignmentPhrases";
  public static final boolean NO_EMPTY_ALIGNMENT = Boolean.parseBoolean(System
      .getProperty(NO_EMPTY_ALIGNMENT_PROPERTY, "true"));

  static int maxPhraseLenF = DEFAULT_MAX_LEN, maxPhraseLenE = DEFAULT_MAX_LEN;
  static int maxExtractedPhraseLenF = DEFAULT_MAX_E_LEN,
      maxExtractedPhraseLenE = DEFAULT_MAX_E_LEN;

  static boolean onlyTightPhrases = false;

  final boolean extractBoundaryPhrases;
  final List<AbstractFeatureExtractor> extractors;
  final AlignmentTemplates alTemps;
  AlignmentTemplateInstance alTemp;
  AlignmentGrid alGrid;

  protected AbstractPhraseExtractor(Properties prop,
      AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {

    // System.err.println("AbstractPhraseExtractor: "+maxPhraseLenF);
    this.alTemps = alTemps;
    this.extractors = extractors;
    this.alTemp = new AlignmentTemplateInstance();
    this.alGrid = new AlignmentGrid(0, 0);

    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(
        SymmetricalWordAlignment.ADD_BOUNDARY_MARKERS_OPT, "false"));
    boolean unalignedBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(
        SymmetricalWordAlignment.UNALIGN_BOUNDARY_MARKERS_OPT, "false"));
    extractBoundaryPhrases = (addBoundaryMarkers && unalignedBoundaryMarkers);
  }

  protected boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    return false;
  }

  protected void addPhraseToIndex(WordAlignment sent, int f1, int f2, int e1,
      int e2, boolean isConsistent, float weight) {

    if (onlyTightPhrases) {
      if (sent.f2e(f1).isEmpty() || sent.f2e(f2).isEmpty()
          || sent.e2f(e1).isEmpty() || sent.e2f(e2).isEmpty())
        return;
    }

    if (ignore(sent, f1, f2, e1, e2))
      return;

    // Check if alTemp meets length requirements:
    if (f2 - f1 >= maxExtractedPhraseLenF || e2 - e1 >= maxExtractedPhraseLenE) {
      if (isConsistent) {
        alGrid.addAlTemp(f1, f2, e1, e2);
      }
      if (DETAILED_DEBUG)
        System.err.printf("skipping too long: %d %d\n", f2 - f1 + 1, e2 - e1
            + 1);
      return;
    }

    // Create alTemp:
    AlignmentTemplateInstance alTemp;
    alTemp = new AlignmentTemplateInstance(sent, f1, f2, e1, e2, weight);
    alGrid.addAlTemp(alTemp, isConsistent);

    alTemps.addToIndex(alTemp);
    alTemps.incrementAlignmentCount(alTemp);
  }

  protected void featurize(WordAlignment sent) {
    int fsize = sent.f().size();
    int esize = sent.e().size();
    // Features are extracted only once all phrases for a given
    // sentence pair are in memory
    for (AbstractFeatureExtractor e : extractors) {
      for (AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
        e.featurizePhrase(alTemp, alGrid);
        if (PRINT_PHRASAL_GRID && fsize < PRINT_GRID_MAX_LEN
            && esize < PRINT_GRID_MAX_LEN)
          alGrid.printAlTempInGrid("phrase id: " + alTemp.getKey(), alTemp,
              System.err);
      }
    }
  }

  @SuppressWarnings("unused")
  private static boolean checkAlignmentConsistency(WordAlignment sent, int f1,
      int f2, int e1, int e2) {
    boolean aligned = false;
    if (f2 - f1 > maxPhraseLenF)
      return false;
    if (e2 - e1 > maxPhraseLenE)
      return false;
    for (int fi = f1; fi <= f2; ++fi)
      for (int ei : sent.f2e(fi)) {
        if (!(e1 <= ei && ei <= e2))
          return false;
        aligned = true;
      }
    if (!aligned)
      return false;
    for (int ei = e1; ei <= e2; ++ei)
      for (int fi : sent.e2f(ei))
        if (!(f1 <= fi && fi <= f2))
          return false;
    return true;
  }

  static void setPhraseExtractionProperties(Properties prop) {

    if (Boolean.parseBoolean(prop.getProperty(
        PhraseExtract.LEX_REORDERING_HIER_OPT, "true"))) {
      prop.setProperty(PhraseExtract.LEX_REORDERING_PHRASAL_OPT, "true");
      maxPhraseLenF = DEFAULT_MAX_LEN_HIER;
      maxPhraseLenE = DEFAULT_MAX_LEN_HIER;
    }

    int max = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_OPT, "-1"));
    int maxF = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_F_OPT, "-1"));
    int maxE = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_E_OPT, "-1"));

    int max2 = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_OPT,
        "-1"));
    int max2F = Integer.parseInt(prop.getProperty(
        MAX_EXTRACTED_PHRASE_LEN_F_OPT, "-1"));
    int max2E = Integer.parseInt(prop.getProperty(
        MAX_EXTRACTED_PHRASE_LEN_E_OPT, "-1"));

    if (max > 0) {
      System.err.printf("Changing default max phrase length: %d -> %d\n",
          maxPhraseLenF, max);
      maxPhraseLenF = maxPhraseLenE = max;
      if (max2 < 0)
        max2 = max;
    }
    if (maxF > 0) {
      System.err.printf("Changing default max phrase length (F): %d -> %d\n",
          maxPhraseLenF, maxF);
      maxPhraseLenF = maxF;
    }
    if (maxE > 0) {
      System.err.printf("Changing default max phrase length (E): %d -> %d\n",
          maxPhraseLenE, maxE);
      maxPhraseLenE = maxE;
    }

    if (max2 > 0) {
      System.err.printf(
          "Changing default max extracted phrase length: %d -> %d\n",
          maxExtractedPhraseLenF, max2);
      maxExtractedPhraseLenF = maxExtractedPhraseLenE = max2;
    }
    if (max2F > 0) {
      System.err.printf(
          "Changing default max extracted phrase length (F): %d -> %d\n",
          maxExtractedPhraseLenF, max2F);
      maxExtractedPhraseLenF = max2F;
    }
    if (max2E > 0) {
      System.err.printf(
          "Changing default max extracted phrase length (E): %d -> %d\n",
          maxExtractedPhraseLenE, max2E);
      maxExtractedPhraseLenE = max2E;
    }
    System.err
        .printf("Maximum internal phrase length (F): %d\n", maxPhraseLenF);
    System.err
        .printf("Maximum internal phrase length (E): %d\n", maxPhraseLenE);
    System.err.printf("Maximum extracted phrase length (F): %d\n",
        maxExtractedPhraseLenF);
    System.err.printf("Maximum extracted phrase length (E): %d\n",
        maxExtractedPhraseLenE);

    assert (maxPhraseLenE >= maxExtractedPhraseLenE);
    assert (maxPhraseLenF >= maxExtractedPhraseLenF);

    String optStr = prop.getProperty(ONLY_TIGHT_PHRASES_OPT);
    onlyTightPhrases = optStr != null && !optStr.equals("false");

    if (Boolean.parseBoolean(prop.getProperty(PhraseExtract.WITH_GAPS_OPT,
        "false"))) {
      DTUPhraseExtractor.setDTUExtractionProperties(prop);
      // OldDTUPhraseExtractor.setDTUExtractionProperties(prop);
    }
  }

  AlignmentGrid getAlGrid() {
    return alGrid;
  }

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    AbstractPhraseExtractor c = (AbstractPhraseExtractor) super.clone();
    c.alGrid = new AlignmentGrid(0, 0);
    c.alTemp = new AlignmentTemplateInstance();
    return c;
  }

}
