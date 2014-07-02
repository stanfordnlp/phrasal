// Phrasal -- A Statistical Machine Translation Toolkit
// for Exploring New Model Features.
// Copyright (c) 2007-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-user@lists.stanford.edu
//    http://nlp.stanford.edu/software/phrasal

package edu.stanford.nlp.mt.train;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.util.IOTools;

/**
 * Loads multiple feature extractors and writes the output to user-specified
 * files.
 * 
 * Extractors are specified by the -extractors argument. Multiple feature
 * extractors may be specified according to this format:
 * 
 *   extractor1=outfile.gz:extractor2=outfile2.gz ...
 * 
 * If two extractors write to the same output file, then the rule scores
 * will be concatenated in the order that the feature extractors appear in
 * the specification.
 * 
 * The default extractor specification is the baseline Moses phrase table
 * features plus a generative lexicalized reordering model.
 * 
 * @author Michel Galley
 * @author Spence Green
 */
public class PhraseExtract {

  public static final String FEATURE_EXTRACTOR_DELIM = ":"; 
  
  public static final String FILE_DELIM = "=";
  
  public static final String DEFAULT_PTABLE_NAME = "phrase-table.gz";
  
  public static final String DEFAULT_LO_NAME = 
      "lo." + LexicalReorderingFeatureExtractor.DEFAULT_MODEL_TYPE + ".gz";
  
  // The Moses default feature set: a phrase table and a lexicalized
  // reordering model.
  private static final String DEFAULT_FEATURE_SET = 
      String.format("%s%s%s%s%s%s", MosesPharoahFeatureExtractor.class.getName(),
          FILE_DELIM, DEFAULT_PTABLE_NAME, FEATURE_EXTRACTOR_DELIM, 
          LexicalReorderingFeatureExtractor.class.getName(),
          FILE_DELIM, DEFAULT_LO_NAME);
  
  static public final String CONFIG_OPT = "config";
  static public final String INPUT_DIR_OPT = "inputDir";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String A_FE_CORPUS_OPT = "efAlign";
  static public final String A_EF_CORPUS_OPT = "feAlign";
  static public final String SYMMETRIZE_OPT = "symmetrization";
  static public final String FEATURE_EXTRACTORS_OPT = "extractors";
  static public final String VERBOSE_OPT = "verbose";
  static public final String HELP_OPT = "help";

  static public final String PHRASE_EXTRACTOR_OPT = "phraseExtractor";
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus";
  static public final String WITH_POS_OPT = "withPos";
  static public final String EMPTY_FILTER_LIST_OPT = "fEmptyFilterList";
  static public final String FILTER_LIST_OPT = "fFilterList";
  static public final String FILTER_CENTERDOT_OPT = "filterCenterDot";
  static public final String REF_PTABLE_OPT = "refFile";
  static public final String SPLIT_SIZE_OPT = "split";
  static public final String OUTPUT_FILE_OPT = "outputFile";
  static public final String NO_ALIGN_OPT = "noAlign";
  static public final String NUM_LINES_OPT = "numLines";
  static public final String MIN_COUNT_OPT = "minCount";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String MAX_FERTILITY_OPT = "maxFertility";
  static public final String LOWERCASE_OPT = "lowercase";
  static public final String MAX_INCONSISTENCIES_OPT = "maxInconsistencies";
  static public final String MEM_USAGE_FREQ_OPT = "memUsageFreq";
  static public final String THREADS_OPT = "threads";
  static public final String WITH_GAPS_OPT = "withGaps";
  static public final String TRIPLE_FILE = "tripleFile";
  static public final String MIN_PHRASE_COUNT = "minCount";
  static public final String OUTPUT_DIR = "outputDir";
  
  // phrase translation probs:  
  static public final String EXACT_PHI_OPT = "exactPhiCounts";
  static public final String IBM_LEX_MODEL_OPT = "ibmLexModel";
  static public final String USE_PMI = "usePmi";
  static public final String NORMALIZE_PMI = "normalizePmi";
  static public final String ONLY_ML_OPT = "onlyML";
  static public final String PTABLE_PHI_FILTER_OPT = "phiFilter"; // p_phi(e|f)
                                                                  // filtering
  static public final String PTABLE_LEX_FILTER_OPT = "lexFilter"; // p_lex(e|f)
                                                                  // filtering

  // orientation models:
  static public final String LEX_REORDERING_TYPE_OPT = "orientationModelType";
  static public final String LEX_REORDERING_PHRASAL_OPT = "phrasalOrientationModel";
  static public final String LEX_REORDERING_HIER_OPT = "hierarchicalOrientationModel";
  static public final String LEX_REORDERING_START_CLASS_OPT = "orientationModelHasStart";
  static public final String LEX_REORDERING_2DISC_CLASS_OPT = "orientationModelHas2Disc";

  static final Set<String> REQUIRED_OPTS = Generics.newHashSet();
  static final Set<String> OPTIONAL_OPTS = Generics.newHashSet();
  static final Set<String> ALL_RECOGNIZED_OPTS = Generics.newHashSet();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(F_CORPUS_OPT, E_CORPUS_OPT));
    OPTIONAL_OPTS.addAll(Arrays.asList(A_CORPUS_OPT, A_EF_CORPUS_OPT,
        A_FE_CORPUS_OPT, SYMMETRIZE_OPT, INPUT_DIR_OPT, FILTER_CORPUS_OPT,
        EMPTY_FILTER_LIST_OPT, FILTER_LIST_OPT, REF_PTABLE_OPT, SPLIT_SIZE_OPT,
        OUTPUT_FILE_OPT, NO_ALIGN_OPT, THREADS_OPT, FEATURE_EXTRACTORS_OPT,
        NUM_LINES_OPT, MIN_COUNT_OPT, WITH_GAPS_OPT,
        START_AT_LINE_OPT, END_AT_LINE_OPT, MAX_FERTILITY_OPT, EXACT_PHI_OPT,
        IBM_LEX_MODEL_OPT, ONLY_ML_OPT, HELP_OPT, PTABLE_PHI_FILTER_OPT,
        PTABLE_LEX_FILTER_OPT, VERBOSE_OPT, LEX_REORDERING_TYPE_OPT,
        LEX_REORDERING_PHRASAL_OPT, LEX_REORDERING_HIER_OPT, OUTPUT_DIR,
        LEX_REORDERING_START_CLASS_OPT, LEX_REORDERING_2DISC_CLASS_OPT,
        MAX_INCONSISTENCIES_OPT, MEM_USAGE_FREQ_OPT, PHRASE_EXTRACTOR_OPT,
        SymmetricalWordAlignment.ADD_BOUNDARY_MARKERS_OPT,
        SymmetricalWordAlignment.UNALIGN_BOUNDARY_MARKERS_OPT, LOWERCASE_OPT,
        AbstractPhraseExtractor.MAX_PHRASE_LEN_OPT,
        AbstractPhraseExtractor.MAX_PHRASE_LEN_E_OPT,
        AbstractPhraseExtractor.MAX_PHRASE_LEN_F_OPT,
        AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_OPT,
        AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_E_OPT,
        AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_F_OPT,
        AbstractPhraseExtractor.ONLY_TIGHT_PHRASES_OPT,
        DTUPhraseExtractor.MAX_SPAN_OPT, DTUPhraseExtractor.MAX_SPAN_E_OPT,
        DTUPhraseExtractor.MAX_SPAN_F_OPT, DTUPhraseExtractor.MAX_SIZE_E_OPT,
        DTUPhraseExtractor.MAX_SIZE_F_OPT, DTUPhraseExtractor.MAX_SIZE_OPT,
        DTUPhraseExtractor.NO_TARGET_GAPS_OPT,
        DTUPhraseExtractor.GAPS_BOTH_SIDES_OPT,
        DTUPhraseExtractor.ALLOW_UNALIGNED_GAPS_OPT,
        DTUPhraseExtractor.ALLOW_LOOSE_GAPS_OPT,
        DTUPhraseExtractor.ALLOW_LOOSE_GAPS_E_OPT,
        DTUPhraseExtractor.ALLOW_LOOSE_GAPS_F_OPT,
        DTUPhraseExtractor.NO_UNALIGNED_SUBPHRASE_OPT,
        DTUPhraseExtractor.NO_UNALIGNED_SUBPHRASE_OPT,
        USE_PMI,
        NORMALIZE_PMI,
        FILTER_CENTERDOT_OPT,
        WITH_POS_OPT,
        TRIPLE_FILE));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  public static final String DEBUG_PROPERTY = "DebugPhraseExtract";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugPhraseExtract";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  protected PhrasePrinter phrasePrinter;
  protected List<AbstractFeatureExtractor> extractors;
  private AbstractPhraseExtractor phraseExtractor = null;

  protected AlignmentTemplates alTemps;
  protected AlignmentTemplateInstance alTemp;
  protected Index<String> featureIndex = new HashIndex<String>();

  boolean doneReadingData;
  boolean verbose;

  private Properties prop;
  private SourceFilter sourceFilter;
  private int startAtLine = -1, endAtLine = -1, numSplits = 0, memUsageFreq,
      nThreads = 0;
  private String fCorpus, eCorpus;
  private String alignCorpus, alignInvCorpus;
  private boolean filterFromDev = false;
  private boolean withAlign;
  private boolean lowercase;
  private String outputDir;
  
  // Triple file format:
  // Single source ||| target ||| alignment triple file
  private final static String tripleDelim = Pattern.quote(AlignmentTemplate.DELIM);
  boolean tripleFile = false;
  
  private SymmetrizationType symmetrizationType = null;

  private int totalPassNumber = 1;
  
  private int minPhraseCount = 0;

  // Data structures to support multiple file output.
  private final Map<AbstractFeatureExtractor,String> extractorToFileString = Generics.newHashMap();
  private final Map<String,PrintStream> fileStringToWriter = Generics.newHashMap();
  
  public PhraseExtract(Properties prop) throws IOException {
    processProperties(prop);
  }

  public void processProperties(Properties prop) throws IOException {

    this.prop = prop;

    boolean withGaps = Boolean.parseBoolean(prop.getProperty(WITH_GAPS_OPT,
        "false"));
    boolean withPos = Boolean.parseBoolean(prop.getProperty(WITH_POS_OPT,
        "false"));

    // Possibly load properties from config file:
    String configFile = prop.getProperty(CONFIG_OPT);
    if (configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch (IOException e) {
        usage();
        throw new RuntimeException(String.format(
            "I/O error while reading configuration file: %s%n", configFile));
      }
    }

    // UCB aligner input dir:
    if (prop.containsKey(INPUT_DIR_OPT)) {
      String inputDir = prop.getProperty(INPUT_DIR_OPT);
      String fId = null, eId = null, cFile = inputDir + "/options.map";
      for (String line : ObjectBank.getLineIterator(cFile)) {
        String[] els = line.split("\\t");
        if (els[0].equals("Data.foreignSuffix")) {
          fId = els[1];
        } else if (els[0].equals("Data.englishSuffix")) {
          eId = els[1];
        }
      }
      if (fId == null || eId == null)
        throw new RuntimeException("Didn't find language identifiers in: "
            + cFile);
      prop.setProperty(F_CORPUS_OPT, inputDir + "/training." + fId);
      prop.setProperty(E_CORPUS_OPT, inputDir + "/training." + eId);
      prop.setProperty(A_CORPUS_OPT, inputDir + "/training.align");
    }
    
    // Single source ||| target ||| alignment triple file
    if (prop.containsKey(TRIPLE_FILE)) {
      String tripleFileFn = prop.getProperty(TRIPLE_FILE);
      tripleFile = true;
      prop.setProperty(F_CORPUS_OPT, tripleFileFn);
      prop.setProperty(E_CORPUS_OPT, tripleFileFn);
      prop.setProperty(A_CORPUS_OPT, tripleFileFn);
    }

    // Check required, optional properties:
    if (!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = Generics.newHashSet(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      usage();
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s%n", missingFields));
    }

    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set<Object> extraFields = Generics.newHashSet(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      usage();
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s%n", extraFields));
    }

    // Analyze props:
    // Mandatory arguments:
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);

    // Alignment arguments:
    symmetrizationType = SymmetrizationType.valueOf(prop.getProperty(
        SYMMETRIZE_OPT, "grow-diag").replace('-', '_'));
    alignCorpus = prop.getProperty(A_CORPUS_OPT);
    if (alignCorpus == null) {
      alignCorpus = prop.getProperty(A_FE_CORPUS_OPT);
      alignInvCorpus = prop.getProperty(A_EF_CORPUS_OPT);
      if (symmetrizationType == SymmetrizationType.none)
        throw new RuntimeException(
            "You need to specify a symmetrization heuristic with GIZA input.");
    }

    // Phrase filtering arguments:
    String fFilterCorpus = prop.getProperty(FILTER_CORPUS_OPT);
    String fFilterList = prop.getProperty(FILTER_LIST_OPT);
    boolean filterCenterDot = Boolean.parseBoolean(prop.getProperty(
        FILTER_CENTERDOT_OPT, "false"));
    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(
        SymmetricalWordAlignment.ADD_BOUNDARY_MARKERS_OPT, "false"));
    boolean emptyFilterList = Boolean.parseBoolean(prop.getProperty(
        EMPTY_FILTER_LIST_OPT, "false"));
    numSplits = Integer.parseInt(prop.getProperty(SPLIT_SIZE_OPT, "0"));
    if (emptyFilterList || fFilterList != null || fFilterCorpus != null)
      filterFromDev = true;

    int maxSpanF = DTUPhraseExtractor.maxSpanF;
    int maxPhraseLenF = AbstractPhraseExtractor.maxPhraseLenF;
    List<String> centerDot = Arrays.asList("·");
    if (withGaps) {
      assert (!addBoundaryMarkers);
      DTUSourceFilter f = new DTUSourceFilter(maxPhraseLenF, maxSpanF);
      if (filterCenterDot)
        f.excludeInList(centerDot);
      f.filterAgainstCorpus(fFilterCorpus);
      sourceFilter = f;
    } else if (withPos) {
      assert (!addBoundaryMarkers);
      PosTaggedSourceFilter f = new PosTaggedSourceFilter(maxSpanF);
      if (filterCenterDot)
        f.excludeInList(centerDot);
      if (fFilterCorpus != null)
        f.filterAgainstCorpus(fFilterCorpus);
      sourceFilter = f;
    } else {
      PhrasalSourceFilter f = new PhrasalSourceFilter(maxSpanF, addBoundaryMarkers);
      if (filterCenterDot)
        f.excludeInList(centerDot);
      if (fFilterList != null) {
        f.filterAgainstList(fFilterList);
      } else if (fFilterCorpus != null) {
        f.filterAgainstCorpus(fFilterCorpus);
      }
      sourceFilter = f;
    }
    sourceFilter.lock();

    // Other optional arguments:
    nThreads = Integer.parseInt(prop.getProperty(THREADS_OPT, "0"));
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT, "-1"));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT, "-2")) + 1;
    memUsageFreq = Integer.parseInt(prop
        .getProperty(MEM_USAGE_FREQ_OPT, "1000"));
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT, "-1"));
    if (numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    withAlign = ! Boolean.parseBoolean(prop.getProperty(NO_ALIGN_OPT, "false"));
    lowercase = Boolean.parseBoolean(prop.getProperty(LOWERCASE_OPT, "false"));
    verbose = Boolean.parseBoolean(prop.getProperty(VERBOSE_OPT, "false"));
    minPhraseCount = PropertiesUtils.getInt(prop, MIN_PHRASE_COUNT, 0);
    outputDir = prop.getProperty(OUTPUT_DIR, null);
  }

  /**
   * Configures the phrase extractor by loading the individual feature
   * extractors.
   */
  @SuppressWarnings("unchecked")
  public void init() {
    
    String[] featureExtractorList = prop.getProperty(FEATURE_EXTRACTORS_OPT, DEFAULT_FEATURE_SET)
        .split(PhraseExtract.FEATURE_EXTRACTOR_DELIM);

    alTemps = new AlignmentTemplates(prop, sourceFilter);
    alTemp = new AlignmentTemplateInstance();
    extractors = Generics.newArrayList();
    phrasePrinter = null;

    // Load the feature extractors
    for (String featureExtractorSpec : featureExtractorList) {
      AbstractFeatureExtractor featureExtractor;
      String[] extractorAndFileName = featureExtractorSpec.trim().split(FILE_DELIM);
      if (extractorAndFileName.length < 2) {
        throw new RuntimeException("Invalid extractor specification: " + featureExtractorSpec);
      }
      String className = extractorAndFileName[0].trim();
      String outFile = extractorAndFileName[1].trim();
      String args[] = null;
      if (extractorAndFileName.length > 2) {
        args = new String[extractorAndFileName.length-2];
        System.arraycopy(extractorAndFileName, 2, args, 0, args.length);
      }
      
      if (outputDir != null) {
        outFile = outputDir + "/" + outFile;
      }
      
      // Load the feature extractor by reflection
      try {
        Class<AbstractFeatureExtractor> extractorClass = (Class<AbstractFeatureExtractor>) ClassLoader
            .getSystemClassLoader().loadClass(className);
        featureExtractor = args == null ? 
            (AbstractFeatureExtractor) extractorClass.newInstance() :
              (AbstractFeatureExtractor) extractorClass.getConstructor(args.getClass()).newInstance(
                  new Object[] { args });
        featureExtractor.init(prop, featureIndex, alTemps);
        extractors.add(featureExtractor);

        System.err.printf("Feature extractor: %s => %s%n"
            ,featureExtractor.getClass().getName(), outFile);
        
        extractorToFileString.put(featureExtractor, outFile);
        if ( ! fileStringToWriter.containsKey(outFile)) {
          fileStringToWriter.put(outFile, IOTools.getWriterFromFile(outFile));
        }
        
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    // Configure the phrase printer
    for (AbstractFeatureExtractor extractor : extractors) {
      if (extractor instanceof PhrasePrinter) {
        if (phrasePrinter == null) {
          phrasePrinter = (PhrasePrinter) extractor;
        } else {
          throw new RuntimeException("Only one feature extractor may implement the PhrasePrinter interface");
        }
      }
    }
    if (phrasePrinter == null) {
      // Default
      phrasePrinter = new PlainPhrasePrinter();
    }
    
    // Configure the phrase extractor
    final boolean withGaps = PropertiesUtils.getBool(prop, WITH_GAPS_OPT, false);
    String phraseExtractorName = prop.getProperty(PHRASE_EXTRACTOR_OPT, null);
    if (phraseExtractorName != null) {
      System.err.println("Phrase extractor: " + phraseExtractorName);
      try {
        Class<AbstractPhraseExtractor> cls = (Class<AbstractPhraseExtractor>) Class
            .forName(phraseExtractorName);
        Constructor<AbstractPhraseExtractor> ct = cls
            .getConstructor(new Class[] { Properties.class,
                AlignmentTemplates.class, List.class });
        phraseExtractor = ct.newInstance(prop, alTemps, extractors);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    
    } else {
      phraseExtractor = withGaps ? new DTUPhraseExtractor(prop, alTemps,
          extractors) : new FlatPhraseExtractor(prop, alTemps, extractors);
    }

    setTotalPassNumber();
  }

  /**
   * Input to the extractor.
   * 
   * @author Spence Green
   *
   */
  private static class ExtractorInput {
    public final int lineNb;
    public final String fLine;
    public final String eLine;
    public final String aLine;
    public ExtractorInput(int lineNb, String fLine, String eLine, String aLine) {
      this.lineNb = lineNb;
      this.fLine = fLine;
      this.eLine = eLine;
      this.aLine = aLine;
    }
    @Override
    public String toString() {
      return String.format("%d: %s ||| %s ||| %s", lineNb, fLine, eLine, aLine);
    }
  }
  
  /**
   * Extract and featurize a sentence pair.
   * 
   * @author Spence Green
   *
   */
  private static class Extractor implements ThreadsafeProcessor<ExtractorInput,Boolean> {
    private final AbstractPhraseExtractor phraseEx;
    private final SymmetricalWordAlignment sent;
    private final Properties properties;
    private final List<AbstractFeatureExtractor> extractorList;

    public Extractor(AbstractPhraseExtractor phraseEx, Properties properties, List<AbstractFeatureExtractor> extractorList) {
      try {
        this.phraseEx = (AbstractPhraseExtractor) phraseEx.clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      sent = new SymmetricalWordAlignment(properties);
      this.properties = properties;
      this.extractorList = extractorList;
    }

    @Override
    public Boolean process(ExtractorInput input) {
      try {
        sent.init(input.lineNb, input.fLine, input.eLine, input.aLine, false, false);
      } catch (Exception e) {
        System.err.println("Invalid line: " + input.toString());
        throw new RuntimeException(e);
      }
      phraseEx.extractPhrases(sent);
      for (AbstractFeatureExtractor e : extractorList) {
        e.featurizeSentence(sent, phraseEx.getAlGrid());
      }
      return true;
    }

    @Override
    public ThreadsafeProcessor<ExtractorInput, Boolean> newInstance() {
      return new Extractor(this.phraseEx, this.properties, this.extractorList);
    }
  }

  // Make as many passes over training data as needed to extract features.
  void extractFromAlignedData() {

    if (!filterFromDev)
      System.err
          .println("WARNING: extracting phrase table not targeted to a specific dev/test corpus!");
    long startTimeMillis = System.currentTimeMillis();

    try {
      for (int passNumber = 0; passNumber < totalPassNumber; ++passNumber) {
        alTemps.enableAlignmentCounts(passNumber == 0);

        // Set current pass:
        for (AbstractFeatureExtractor e : extractors)
          e.setCurrentPass(passNumber);

        doneReadingData = false;

        MulticoreWrapper<ExtractorInput,Boolean> wrapper = 
            new MulticoreWrapper<ExtractorInput,Boolean>(nThreads, 
                new Extractor(phraseExtractor, prop, extractors), false);

        boolean useGIZA = alignInvCorpus != null;

        // Read data and process data:
        if (passNumber > 0)
          System.err
              .println("Some feature extractor needs an additional pass over the data.");
        System.err.printf(
            "Pass %d on training data (max phrase len: %d,%d)...%nLine",
            passNumber + 1, AbstractPhraseExtractor.maxPhraseLenF,
            AbstractPhraseExtractor.maxPhraseLenE);
        LineNumberReader aInvReader = null, fReader = IOTools
            .getReaderFromFile(fCorpus), eReader = IOTools
            .getReaderFromFile(eCorpus), aReader = IOTools
            .getReaderFromFile(alignCorpus);
        if (useGIZA)
          aInvReader = IOTools.getReaderFromFile(alignInvCorpus);

        int lineNb = 0;
        for (String fLine;; ++lineNb) {
          fLine = fReader.readLine();

          boolean done = (fLine == null || lineNb == endAtLine);

          if (tripleFile && !done) {
            fLine = fLine.split(" \\|\\|\\| ")[0];
          }

          if (lineNb % memUsageFreq == 0 || done) {
            // long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory() / (1 << 20);
            // double totalStepSecs = (System.currentTimeMillis() -
            // startStepTimeMillis)/1000.0;
            // startStepTimeMillis = System.currentTimeMillis();
            System.err.printf(" %d (mem=%dm)...", lineNb, freeMemory);
            // if (verbose)
            // System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...%n",
            // lineNb, totalStepSecs, totalMemory, freeMemory,
            // alTemps.getSizeInfo());
          }

          if (done) {
            if (startAtLine >= 0 || endAtLine >= 0)
              System.err.printf("%nRange done: [%d-%d], current line is %d.%n",
                  startAtLine, endAtLine - 1, lineNb);
            break;
          }

          String eLine = eReader.readLine();
          if (tripleFile) {
            eLine = eLine.split(tripleDelim)[1].trim();
          }
          if (eLine == null)
            throw new IOException("Target-language corpus is too short!");

          boolean skipLine = (fLine.isEmpty() || eLine.isEmpty());

          // Read alignment:
          String aLine = null;
          if (useGIZA) {
            String ef1 = aReader.readLine();
            String ef2 = aReader.readLine();
            String ef3 = aReader.readLine();
            String fe1 = aInvReader.readLine();
            String fe2 = aInvReader.readLine();
            String fe3 = aInvReader.readLine();
            if (!skipLine) {
              GIZAWordAlignment gizaAlign = new GIZAWordAlignment(fe1, fe2,
                  fe3, ef1, ef2, ef3);
              SymmetricalWordAlignment symAlign = AlignmentSymmetrizer
                  .symmetrize(gizaAlign, symmetrizationType);
              symAlign.reverse();
              aLine = symAlign.toString().trim();
            }
          } else {
            aLine = aReader.readLine();
            if (tripleFile) {
              String[] toks = aLine.split(tripleDelim);
              if (toks.length >= 3) {
                aLine = aLine.split(tripleDelim)[2].trim();
              } else {
                aLine = "";
              }
            }
            if (aLine == null)
              throw new IOException("Alignment file is too short!");
          }
          if (skipLine || aLine.isEmpty())
            continue;

          if (lineNb < startAtLine)
            continue;
          if (DETAILED_DEBUG) {
            System.err.printf("e(%d): %s%n", lineNb, eLine);
            System.err.printf("f(%d): %s%n", lineNb, fLine);
            System.err.printf("a(%d): %s%n", lineNb, aLine);
          }
          if (lowercase) {
            fLine = fLine.toLowerCase();
            eLine = eLine.toLowerCase();
          }
          
          wrapper.put(new ExtractorInput(lineNb,fLine, eLine, aLine));
          while(wrapper.peek()) {
            boolean success = wrapper.poll();
            if ( ! success) {
              throw new RuntimeException("Extractor failure");
            }
          }
        }

        if (eReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Target-language corpus contains extra lines!");
        if (aReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Alignment file contains extra lines!");

        fReader.close();
        eReader.close();
        aReader.close();

        doneReadingData = true;
        wrapper.join();
        while(wrapper.peek()) {
          boolean success = wrapper.poll();
          if ( ! success) {
            throw new RuntimeException("Extractor failure");
          }
        }

        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        System.err.printf("%nDone with pass %d. Seconds: %.3f.%n",
            passNumber + 1, totalTimeSecs);
      }

      // just let each extractor output some stuff to the STDERR
      for (AbstractFeatureExtractor e : extractors)
        e.report();

    } catch (IOException e) {
      e.printStackTrace();
    } 
  }

  // Write combined features to a stream.
  boolean write(boolean withAlign) {

    final long startTime = System.nanoTime();

    System.err.printf("Phrases in memory: %d%n", alTemps.size());
    int phrasesWritten = 0;

    for (int idx = 0; idx < alTemps.size(); ++idx) {
      if (!alTemps.reconstructAlignmentTemplate(alTemp, idx)) {
        continue;
      }
      
      // Filter phrases that have occured less than n times
      // Note that by filtering the phrases here, the generative extractor scores 
      // are not necessarily correct.
      // TODO(spenceg): This should be replaced with leave-one-out.
      if (alTemps.getAlignmentCount(alTemp) < minPhraseCount) {
        continue;
      }
      
      StringBuilder ruleStr = new StringBuilder();
      ruleStr.append(phrasePrinter.toString(alTemp, withAlign));
      ruleStr.append(" ").append(AlignmentTemplate.DELIM).append(" ");

      Map<String,StringBuilder> fileToScores = Generics.newHashMap();
      for (String file : fileStringToWriter.keySet()) {
        fileToScores.put(file, new StringBuilder());
      }
      boolean skip = false;
      for (AbstractFeatureExtractor e : extractors) {
        Object scores = e.score(alTemp);
        if (scores == null) {
          skip = true;
          break;
        }
        
        String outFileName = extractorToFileString.get(e);
        if (outFileName == null) {
          // Impossible unless the init() method is changed....
          throw new RuntimeException("No output file for extractor: " + e.getClass().getName());
        }
        StringBuilder scoreStr = fileToScores.get(outFileName);
        if (scoreStr == null) {
          // Impossible unless the init() method is changed....
          throw new RuntimeException("No score collector for output file: " + outFileName);  
        }
        
        if (scores instanceof float[]) { // as dense vector
          float[] scoreArray = (float[]) scores;
          for (float score : scoreArray) {
            score = (score > 0.0) ? (float) Math.log(score) : score;
            scoreStr.append(score).append(" ");
          }
        
        } else if (scores instanceof double[]) {
          double[] scoreArray = (double[]) scores;
          for (double score : scoreArray) {
            score = (score > 0.0) ? Math.log(score) : score;
            scoreStr.append((float) score).append(" ");
          }
        
        } else {
          throw new UnsupportedOperationException(
              "AbstractFeatureExtractor should return double[] or Counter, not "
                  + scores.getClass());
        }
      }
      if (!skip) {
        for (String file : fileStringToWriter.keySet()) {
          StringBuilder scores = fileToScores.get(file);
          String line = ruleStr.toString() + scores.toString();
          PrintStream outfile = fileStringToWriter.get(file);
          outfile.println(line);
        }
        ++phrasesWritten;
      }
    }

    System.err.printf("Phrases written: %d%n", phrasesWritten);
    double elapsedTime = ((double) (System.nanoTime() - startTime)) / 1e9;
    System.err.printf("Done generating phrase table. Elapsed time: %.3fs.%n",
        elapsedTime);
    
    return true;
  }

  private void setTotalPassNumber() {
    totalPassNumber = 0;
    for (AbstractFeatureExtractor ex : extractors) {
      int p = ex.getRequiredPassNumber();
      if (p > totalPassNumber)
        totalPassNumber = p;
    }
  }

  public void extractAll() {
    if (filterFromDev) {
      int sz = sourceFilter.size();
      int size = 1 + (numSplits == 0 ? sz : sz / numSplits);
      int startLine = 0;
      while (startLine < sz) {
        init();
        sourceFilter.setRange(startLine, startLine + size);
        extractFromAlignedData();
        write(withAlign);
        startLine += size;
      }
    } else {
      init();
      extractFromAlignedData();
      write(withAlign);
    }
    for (PrintStream file : fileStringToWriter.values()) {
      file.close();
    }
  }

  static void usage() {
    System.err
        .printf("Usage: java edu.stanford.nlp.mt.train.PhraseExtract [ARGS]%n"
            + "Sets of mandatory arguments (user must select either set 1, 2, or 3):%n"
            + "Set 1:%n"
            + " -fCorpus <file> : source-language corpus%n"
            + " -eCorpus <file> : target-language corpus%n"
            + " -align <file> : alignment file (Moses format)%n"
            + "Set 2:%n"
            + " -fCorpus <file> : source-language corpus%n"
            + " -eCorpus <file> : target-language corpus%n"
            + " -feAlign <file> : f-e alignment file (GIZA format)%n"
            + " -efAlign <file> : e-f alignment file (GIZA format)%n"
            + "Set 3:%n"
            + " -inputDir <directory> : alignment directory created by Berkeley aligner v2.1%n"
            + "Set 4:%n"
            + " -tripleFile <file> : source ||| target ||| alignment triple format%n"
            + "Optional arguments:%n"
            + " -symmetrization <type> : alignment symmetrization heuristic (expects -feAlign and -efAlign)%n"
            + " -extractors <class1>[:<class2>:...:<classN>] : feature extractors%n"
            + " -fFilterCorpus <file> : filter against a specific dev/test set%n"
            + " -fFilterList <file> : phrase extraction restricted to this list%n"
            + " -split <N> : split filter list into N chunks%n"
            + "  (divides memory usage by N, but multiplies running time by N)%n"
            + " -refFile <file> : check features against a Moses phrase table%n"
            + " -maxLen <n> : max phrase length%n"
            + " -maxLenF <n> : max phrase length (source-language)%n"
            + " -maxLenE <n> : max phrase length (target-language)%n"
            + " -numLines <n> : number of lines to process (<0 : all)%n"
            + " -startAtLine <n> : start at line <n> (<0 : all)%n"
            + " -endAtLine <n> : end at line <n> (<0 : all)%n"
            + " -noAlign : do not specify alignment in phrase table%n"
            + " -verbose : enable verbose mode%n"
            + " -minCount <n> : Retain only phrases that occur >= n times%n"
            + " -outputDir path : Output files to <path>%n");
  }

  /**
   * Extract phrases from an aligned bitext.
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 1 && args[0].equals("-help")) {
      usage();
      return;
    }

    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    System.err.printf("Extraction started at %s on %s.%n",
        formatter.format(new Date()), InetAddress.getLocalHost().getHostName());

    Properties prop = StringUtils.argsToProperties(args);
    System.err.println("Properties: " + prop.toString());
    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);

    try {
      PhraseExtract e = new PhraseExtract(prop);
      e.extractAll();
    } catch (Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("Extraction ended at " + formatter.format(new Date()));
  }
}
