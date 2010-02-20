package edu.stanford.nlp.mt.train;

import java.util.*;

/**
 * The preferred way of instanciating PhraseExtractor is to extend AbstractPhraseExtractor.
 * Its constructor takes a list of AbstractFeatureExtractor as argument, which are then 
 * used in {@link #extractPhrase(WordAlignment,int,int,int,int,boolean,float)} and
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

  static int maxPhraseLenF = 7, maxPhraseLenE = 7;
  static int maxExtractedPhraseLenF = 7, maxExtractedPhraseLenE = 7;

  static boolean onlyTightPhrases = false, onlyTightDTUs = false;

  AlignmentGrid alGrid = null;
  boolean needAlGrid = false;

  final boolean extractBoundaryPhrases;
  
  List<AbstractFeatureExtractor> extractors;
  final AlignmentTemplates alTemps;
  AlignmentTemplateInstance alTemp, alTemp2;

  public Object clone() throws CloneNotSupportedException {
    AbstractPhraseExtractor c = (AbstractPhraseExtractor) super.clone();
    c.alGrid = new AlignmentGrid(0,0);
    c.alTemp = new AlignmentTemplateInstance();
    c.alTemp2 = new AlignmentTemplateInstance();
    return c;
  }

  public AbstractPhraseExtractor(Properties prop, AlignmentTemplates alTemps, List<AbstractFeatureExtractor> extractors) {

    System.err.println("AbstractPhraseExtractor: "+maxPhraseLenF);
    this.alTemps = alTemps;
    this.extractors = extractors;
    this.alTemp = new AlignmentTemplateInstance();
    this.alTemp2 = new AlignmentTemplateInstance();
    needAlGrid = false;
    
    if(PRINT_GRID_MAX_LEN >= 0)
      needAlGrid = true;
    else {
      for(AbstractFeatureExtractor ex : extractors)
        if(ex.needAlGrid()) {
          needAlGrid = true;
          break;
        }
    }

    if(needAlGrid)
      alGrid = new AlignmentGrid(0,0);
    System.err.println("Using AlignmentGrid: "+needAlGrid);

    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(SymmetricalWordAlignment.ADD_BOUNDARY_MARKERS_OPT,"false"));
    boolean unalignedBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(SymmetricalWordAlignment.UNALIGN_BOUNDARY_MARKERS_OPT,"false"));
    extractBoundaryPhrases = (addBoundaryMarkers && unalignedBoundaryMarkers);
  }

  public AlignmentGrid getAlGrid() { return alGrid; }

  boolean ignore(WordAlignment sent, int f1, int f2, int e1, int e2) {
    return false;
  }

  @Override
  public void setSentenceInfo(WordAlignment sent, String infoStr) {}

  void extractPhrase(WordAlignment sent, int f1, int f2, int e1, int e2, boolean isConsistent, float weight) {

    if (onlyTightPhrases) {
      if (sent.f2e(f1).size() == 0 || sent.f2e(f2).size() == 0 || sent.e2f(e1).size() == 0 || sent.e2f(e2).size() == 0)
        return;
    }

    if(ignore(sent, f1, f2, e1, e2))
      return;

    // Check if alTemp meets length requirements:
    if(f2-f1>=maxExtractedPhraseLenF || e2-e1>=maxExtractedPhraseLenE) {
      if(needAlGrid && isConsistent) {
        alGrid.addAlTemp(f1,f2,e1,e2);
      }
      if(DETAILED_DEBUG)
        System.err.printf("skipping too long: %d %d\n",f2-f1+1,e2-e1+1);
      return;
    }

    // Create alTemp:
    AlignmentTemplateInstance alTemp;
    if(needAlGrid) {
      alTemp = new AlignmentTemplateInstance(sent,f1,f2,e1,e2,weight);
      alGrid.addAlTemp(alTemp, isConsistent);
    } else {
      alTemp = this.alTemp;
      alTemp.init(sent,f1,f2,e1,e2,weight);
    }

    alTemps.addToIndex(alTemp);
    alTemps.incrementAlignmentCount(alTemp);

    // Run each feature extractor for each altemp:
    if(!needAlGrid)
      for(AbstractFeatureExtractor e : extractors) {
        e.extract(alTemp, null);
      }
  }

  // For DTU phrase extraction:
  AlignmentTemplateInstance extractPhrase(WordAlignment sent, BitSet fs, BitSet es, boolean fContiguous, boolean eContiguous, boolean isConsistent) {

    if (onlyTightPhrases || (onlyTightDTUs && (!fContiguous || !eContiguous))) {
      int f1 = fs.nextSetBit(0);
      int f2 = fs.length()-1;
      int e1 = es.nextSetBit(0);
      int e2 = es.length()-1;
      if (sent.f2e(f1).size() == 0 || sent.f2e(f2).size() == 0 || sent.e2f(e1).size() == 0 || sent.e2f(e2).size() == 0)
        return null;
    }

    // Check if alTemp meets length requirements:
    if(fs.cardinality() > maxExtractedPhraseLenF || es.cardinality() > maxExtractedPhraseLenE) {
      if(needAlGrid && isConsistent && fContiguous && eContiguous) {
        alGrid.addAlTemp(fs.nextSetBit(0), fs.length()-1, es.nextSetBit(0), es.length()-1);
      }
      if(DETAILED_DEBUG)
        System.err.printf("skipping too long: %d %d\n",fs.cardinality(),es.cardinality());
      return null;
    }

    // Create alTemp:
    AlignmentTemplateInstance alTemp;
    if(needAlGrid) {
      alTemp = new AlignmentTemplateInstance(sent, fs, es, fContiguous, eContiguous);
      alGrid.addAlTemp(alTemp, isConsistent);
    } else {
      alTemp = this.alTemp2;
      alTemp.init(sent, fs, es, fContiguous, eContiguous);
    }

    alTemps.addToIndex(alTemp);
    alTemps.incrementAlignmentCount(alTemp);

    // Run each feature extractor for each altemp:
    if(!needAlGrid) {
      for(AbstractFeatureExtractor e : extractors) {
        e.extract(alTemp, null);
      }
    }
    return alTemp;
  }

  public void extractPhrasesFromAlGrid(WordAlignment sent) {
    assert(needAlGrid);
    int fsize = sent.f().size();
    int esize = sent.e().size();
    // Features are extracted only once all phrases for a given
    // sentece pair are in memory
    for(AbstractFeatureExtractor e : extractors)
      for(AlignmentTemplateInstance alTemp : alGrid.getAlTemps()) {
        e.extract(alTemp, alGrid);
        if(PRINT_PHRASAL_GRID && fsize < PRINT_GRID_MAX_LEN && esize < PRINT_GRID_MAX_LEN)
          alGrid.printAlTempInGrid
            ("phrase id: "+alTemp.getKey(),sent,alTemp,System.err);
      }
  }

  boolean checkAlignmentConsistency(WordAlignment sent, int f1, int f2, int e1, int e2) {
    boolean aligned = false;
    if(f2-f1 > maxPhraseLenF) return false;
    if(e2-e1 > maxPhraseLenE) return false;
    for(int fi=f1; fi<=f2; ++fi)
      for(int ei : sent.f2e(fi)) {
        if(!(e1 <= ei && ei <= e2))
          return false;
        aligned = true;
      }
    if(!aligned) return false;
    for(int ei=e1; ei<=e2; ++ei)
      for(int fi : sent.e2f(ei))
        if(!(f1 <= fi && fi <= f2))
          return false;
    return true;
  }

  public static void setPhraseExtractionProperties(Properties prop) {
    int max = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_OPT,"-1"));
    int maxF = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_F_OPT,"-1"));
    int maxE = Integer.parseInt(prop.getProperty(MAX_PHRASE_LEN_E_OPT,"-1"));

    int max2 = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_OPT,"-1"));
    int max2F = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_F_OPT,"-1"));
    int max2E = Integer.parseInt(prop.getProperty(MAX_EXTRACTED_PHRASE_LEN_E_OPT,"-1"));

    if(max > 0) {
      System.err.printf("changing default max phrase length: %d -> %d\n", maxPhraseLenF, max);
      maxPhraseLenF = maxPhraseLenE = max;
      if(max2 < 0)
        max2 = max;
    }
    if(maxF > 0) {
      System.err.printf("changing default max phrase length (F): %d -> %d\n", maxPhraseLenF, maxF);
      maxPhraseLenF = maxF;
    }
    if(maxE > 0) {
      System.err.printf("changing default max phrase length (E): %d -> %d\n", maxPhraseLenE, maxE);
      maxPhraseLenE = maxE;
    }

    if(max2 > 0) {
      System.err.printf("changing default max extracted phrase length: %d -> %d\n", maxExtractedPhraseLenF, max2);
      maxExtractedPhraseLenF = maxExtractedPhraseLenE = max2;
    }
    if(max2F > 0) {
      System.err.printf("changing default max extracted phrase length (F): %d -> %d\n", maxExtractedPhraseLenF, max2F);
      maxExtractedPhraseLenF = max2F;
    }
    if(max2E > 0) {
      System.err.printf("changing default max extracted phrase length (E): %d -> %d\n", maxExtractedPhraseLenE, max2E);
      maxExtractedPhraseLenE = max2E;
    }
    System.err.printf("maximum phrase length (F): %d\n", maxPhraseLenF);
    System.err.printf("maximum phrase length (E): %d\n", maxPhraseLenE);
    System.err.printf("maximum extracted phrase length (F): %d\n", maxExtractedPhraseLenF);
    System.err.printf("maximum extracted phrase length (E): %d\n", maxExtractedPhraseLenE);

    assert(maxPhraseLenE >= maxExtractedPhraseLenE);
    assert(maxPhraseLenF >= maxExtractedPhraseLenF);

    String optStr = prop.getProperty(ONLY_TIGHT_PHRASES_OPT);
    onlyTightPhrases = optStr != null && !optStr.equals("false");

    optStr = prop.getProperty(ONLY_TIGHT_DTUS_OPT);
    onlyTightDTUs = optStr != null && !optStr.equals("false");

    DTUPhraseExtractor.setDTUExtractionProperties(prop);
  }
}
