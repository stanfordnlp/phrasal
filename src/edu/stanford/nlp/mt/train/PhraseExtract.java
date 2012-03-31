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

import edu.stanford.nlp.mt.tools.Interpreter;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;

import edu.stanford.nlp.mt.base.IOTools;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

/**
 * Combines multiple phrase-level feature extractors into one, and prints their
 * outputs to STDOUT.
 * 
 * @author Michel Galley
 */
public class PhraseExtract {

  static public final String CONFIG_OPT = "config";
  static public final String INPUT_DIR_OPT = "inputDir";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String A_FE_CORPUS_OPT = "efAlign";
  static public final String A_EF_CORPUS_OPT = "feAlign";
  static public final String SYMMETRIZE_OPT = "symmetrization";
  static public final String EXTRACTORS_OPT = "extractors";
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
  static public final String PRINT_FEATURE_NAMES_OPT = "printFeatureNames";
  static public final String MIN_COUNT_OPT = "minCount";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String MAX_FERTILITY_OPT = "maxFertility";
  static public final String LOWERCASE_OPT = "lowercase";
  static public final String MAX_INCONSISTENCIES_OPT = "maxInconsistencies";
  static public final String MEM_USAGE_FREQ_OPT = "memUsageFreq";
  static public final String THREADS_OPT = "threads";
  static public final String WITH_GAPS_OPT = "withGaps";

  // phrase translation probs:
  static public final String EXACT_PHI_OPT = "exactPhiCounts";
  static public final String IBM_LEX_MODEL_OPT = "ibmLexModel";
  static public final String USE_PMI = "usePmi";
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

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(F_CORPUS_OPT, E_CORPUS_OPT));
    OPTIONAL_OPTS.addAll(Arrays.asList(A_CORPUS_OPT, A_EF_CORPUS_OPT,
        A_FE_CORPUS_OPT, SYMMETRIZE_OPT, INPUT_DIR_OPT, FILTER_CORPUS_OPT,
        EMPTY_FILTER_LIST_OPT, FILTER_LIST_OPT, REF_PTABLE_OPT, SPLIT_SIZE_OPT,
        OUTPUT_FILE_OPT, NO_ALIGN_OPT, THREADS_OPT, EXTRACTORS_OPT,
        NUM_LINES_OPT, PRINT_FEATURE_NAMES_OPT, MIN_COUNT_OPT, WITH_GAPS_OPT,
        START_AT_LINE_OPT, END_AT_LINE_OPT, MAX_FERTILITY_OPT, EXACT_PHI_OPT,
        IBM_LEX_MODEL_OPT, ONLY_ML_OPT, HELP_OPT, PTABLE_PHI_FILTER_OPT,
        PTABLE_LEX_FILTER_OPT, VERBOSE_OPT, LEX_REORDERING_TYPE_OPT,
        LEX_REORDERING_PHRASAL_OPT, LEX_REORDERING_HIER_OPT,
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
        FILTER_CENTERDOT_OPT,
        WITH_POS_OPT));
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
  // each extract is allowed to have one file that contains extra information
  // (one line per sentence)
  private List<String> infoFileForExtractors;
  private List<String> infoLinesForExtractors;
  private AbstractPhraseExtractor phraseExtractor = null;

  protected AlignmentTemplates alTemps;
  protected AlignmentTemplateInstance alTemp;
  protected Index<String> featureIndex = new HashIndex<String>();

  private final List<Thread> threads = new LinkedList<Thread>();
  private final LinkedBlockingQueue<Pair<Integer, String[]>> dataQueue = new LinkedBlockingQueue<Pair<Integer, String[]>>(
      1000);
  boolean doneReadingData;
  boolean verbose;

  private Properties prop;
  private SourceFilter sourceFilter;
  private int startAtLine = -1, endAtLine = -1, numSplits = 0, memUsageFreq,
      nThreads = 0;
  private String fCorpus, eCorpus, phraseExtractorInfoFile, outputFile;
  private String alignCorpus, alignInvCorpus;
  private boolean filterFromDev = false, printFeatureNames = true, withAlign,
      lowercase;
  private SymmetrizationType symmetrizationType = null;

  private int totalPassNumber = 1;

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
            "I/O error while reading configuration file: %s\n", configFile));
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

    // Check required, optional properties:
    if (!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      usage();
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s\n", missingFields));
    }

    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set<Object> extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      usage();
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s\n", extraFields));
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
    printFeatureNames = Boolean.parseBoolean(prop.getProperty(
        PRINT_FEATURE_NAMES_OPT, "true"));
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT, "-1"));
    if (numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    withAlign = !Boolean.parseBoolean(prop.getProperty(NO_ALIGN_OPT, "false"));
    lowercase = Boolean.parseBoolean(prop.getProperty(LOWERCASE_OPT, "false"));
    verbose = Boolean.parseBoolean(prop.getProperty(VERBOSE_OPT, "false"));
    outputFile = prop.getProperty(OUTPUT_FILE_OPT);
  }

  public void init() {

    String exsString = prop.getProperty(EXTRACTORS_OPT);
    if (exsString == null || exsString.equals("") || exsString.equals("moses"))
      exsString = MosesPharoahFeatureExtractor.class.getName() + ":"
          + LexicalReorderingFeatureExtractor.class.getName();

    alTemps = new AlignmentTemplates(prop, sourceFilter);
    alTemp = new AlignmentTemplateInstance();
    extractors = new ArrayList<AbstractFeatureExtractor>();
    infoFileForExtractors = new ArrayList<String>();

    boolean withGaps = Boolean.parseBoolean(prop.getProperty(WITH_GAPS_OPT,
        "false"));

    for (String exStr : exsString.split(":")) {
      try {
        AbstractFeatureExtractor fe;
        String[] extractorAndInfoFile = exStr.split("=");
        String infoFilename = null;

        // if the extractor string contains "=", then assume
        // that A in A=B is the extractor class name, and
        // B is an "info" file with same number of lines as sentence pairs.
        if (extractorAndInfoFile.length == 2) {
          infoFilename = extractorAndInfoFile[1];
          exStr = extractorAndInfoFile[0];
          System.err.printf("File read by extractor %s: %s.\n", exStr,
              infoFilename);
        } else if (extractorAndInfoFile.length != 1) {
          throw new RuntimeException("extractor argument format error");
        }

        // if exStr contains parentheses, assume it is a call to a constructor
        // (without the "new"):
        int pos = exStr.indexOf('(');
        if (pos >= 0) {
          StringBuffer constructor = new StringBuffer("new ").append(exStr);
          System.err.println("Running constructor: " + constructor);
          Interpreter interpreter = (Interpreter) Class.forName(
              "edu.stanford.nlp.mt.BshInterpreter").newInstance();
          fe = (AbstractFeatureExtractor) interpreter.evalString(constructor
              .toString());
        } else {
          @SuppressWarnings("unchecked")
          Class<AbstractFeatureExtractor> cls = (Class<AbstractFeatureExtractor>) Class
              .forName(exStr);
          Constructor<AbstractFeatureExtractor> ct = cls
              .getConstructor(new Class[] {});
          fe = ct.newInstance();
        }

        fe.init(prop, featureIndex, alTemps);
        if (extractors.isEmpty()) {
          if (fe instanceof PhrasePrinter)
            phrasePrinter = (PhrasePrinter) fe;
          else
            phrasePrinter = new PlainPhrasePrinter();
        }
        extractors.add(fe);
        infoFileForExtractors.add(infoFilename);
        System.err.println("Instantiating feature extractor: "
            + fe.getClass().getName());

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    String phraseExtractorName = prop.getProperty(PHRASE_EXTRACTOR_OPT);
    if (phraseExtractorName != null) {
      String[] fields = phraseExtractorName.split("=");
      if (fields.length == 2)
        phraseExtractorInfoFile = fields[1];
      else if (fields.length != 1)
        throw new RuntimeException("Can't parse: " + phraseExtractorName);
      phraseExtractorName = fields[0];
      System.err.println("Phrase extractor: " + Arrays.toString(fields));
      try {
        @SuppressWarnings("unchecked")
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

  class Extractor extends Thread {

    final PhraseExtract ex;
    final AbstractPhraseExtractor phraseEx;
    final LinkedBlockingQueue<Pair<Integer, String[]>> dataQueue;
    final SymmetricalWordAlignment sent = new SymmetricalWordAlignment(prop);

    Extractor(PhraseExtract ex, LinkedBlockingQueue<Pair<Integer, String[]>> q) {
      this.ex = ex;
      try {
        this.phraseEx = (AbstractPhraseExtractor) phraseExtractor.clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      dataQueue = q;
    }

    @Override
    public void run() {
      try {
        while (!dataQueue.isEmpty() || !ex.doneReadingData) {
          Pair<Integer, String[]> p = dataQueue.poll();
          if (p != null) {
            String[] lines = p.second();
            ex.processLine(phraseEx, p.first(), sent, lines[0], lines[1],
                lines[2], lines[3]);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

  }

  // Make as many passes over training data as needed to extract features.
  void extractFromAlignedData() {

    if (!filterFromDev)
      System.err
          .println("WARNING: extracting phrase table not targeted to a specific dev/test corpus!");
    long startTimeMillis = System.currentTimeMillis();

    SymmetricalWordAlignment sent = new SymmetricalWordAlignment(prop);

    try {
      for (int passNumber = 0; passNumber < totalPassNumber; ++passNumber) {
        alTemps.enableAlignmentCounts(passNumber == 0);

        // Set current pass:
        for (AbstractFeatureExtractor e : extractors)
          e.setCurrentPass(passNumber);

        doneReadingData = false;

        assert (threads.isEmpty());
        assert (dataQueue.isEmpty());
        for (int i = 0; i < nThreads; ++i) {
          // System.err.printf("Creating thread %d...\n", i);
          Extractor thread = new Extractor(this, dataQueue);
          thread.start();
          threads.add(thread);
        }

        boolean useGIZA = alignInvCorpus != null;

        // Read data and process data:
        if (passNumber > 0)
          System.err
              .println("Some feature extractor needs an additional pass over the data.");
        System.err.printf(
            "Pass %d on training data (max phrase len: %d,%d)...\nLine",
            passNumber + 1, AbstractPhraseExtractor.maxPhraseLenF,
            AbstractPhraseExtractor.maxPhraseLenE);
        LineNumberReader pReader = null, aInvReader = null, fReader = IOTools
            .getReaderFromFile(fCorpus), eReader = IOTools
            .getReaderFromFile(eCorpus), aReader = IOTools
            .getReaderFromFile(alignCorpus);
        if (useGIZA)
          aInvReader = IOTools.getReaderFromFile(alignInvCorpus);
        if (phraseExtractorInfoFile != null)
          pReader = IOTools.getReaderFromFile(phraseExtractorInfoFile);

        // make Readers from the info files for each extractors
        List<LineNumberReader> infoReaders = new ArrayList<LineNumberReader>();
        for (String infoFile : infoFileForExtractors) {
          LineNumberReader r = null;
          if (infoFile != null) {
            r = IOTools.getReaderFromFile(infoFile);
          }
          infoReaders.add(r);
        }

        int lineNb = 0;
        for (String fLine;; ++lineNb) {
          fLine = fReader.readLine();
          boolean done = (fLine == null || lineNb == endAtLine);

          if (lineNb % memUsageFreq == 0 || done) {
            // long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory() / (1 << 20);
            // double totalStepSecs = (System.currentTimeMillis() -
            // startStepTimeMillis)/1000.0;
            // startStepTimeMillis = System.currentTimeMillis();
            System.err.printf(" %d (mem=%dm)...", lineNb, freeMemory);
            // if (verbose)
            // System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
            // lineNb, totalStepSecs, totalMemory, freeMemory,
            // alTemps.getSizeInfo());
          }

          if (done) {
            if (startAtLine >= 0 || endAtLine >= 0)
              System.err.printf("\nRange done: [%d-%d], current line is %d.\n",
                  startAtLine, endAtLine - 1, lineNb);
            break;
          }

          String eLine = eReader.readLine();
          if (eLine == null)
            throw new IOException("Target-language corpus is too short!");
          String pLine = pReader == null ? null : pReader.readLine();

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
            if (aLine == null)
              throw new IOException("Alignment file is too short!");
          }
          if (skipLine || aLine.isEmpty())
            continue;

          // Read line with extra/custom information:
          if (nThreads == 0) {
            infoLinesForExtractors = new ArrayList<String>();
            for (LineNumberReader infoReader : infoReaders) {
              String infoLine = null;
              if (infoReader != null) {
                infoLine = infoReader.readLine();
                if (infoLine == null)
                  throw new IOException(
                      "Info file for one extractor is too short!");
              }
              infoLinesForExtractors.add(infoLine);
            }
          }

          if (lineNb < startAtLine)
            continue;
          if (DETAILED_DEBUG) {
            System.err.printf("e(%d): %s\n", lineNb, eLine);
            System.err.printf("f(%d): %s\n", lineNb, fLine);
            System.err.printf("a(%d): %s\n", lineNb, aLine);
          }
          if (lowercase) {
            fLine = fLine.toLowerCase();
            eLine = eLine.toLowerCase();
          }
          if (threads.isEmpty()) {
            processLine(phraseExtractor, lineNb, sent, fLine, eLine, aLine,
                pLine);
          } else {
            dataQueue.put(new Pair<Integer, String[]>(lineNb, new String[] {
                fLine, eLine, aLine, pLine }));
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

        for (int i = 0; i < nThreads; ++i)
          threads.get(i).join();

        assert (dataQueue.isEmpty());
        threads.clear();

        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        System.err.printf("\nDone with pass %d. Seconds: %.3f.\n",
            passNumber + 1, totalTimeSecs);
      }

      // just let each extractor output some stuff to the STDERR
      for (AbstractFeatureExtractor e : extractors)
        e.report();

    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void processLine(AbstractPhraseExtractor ex, int lineNb,
      SymmetricalWordAlignment sent, String fLine, String eLine, String aLine,
      String pLine) throws IOException {
    sent.init(lineNb, fLine, eLine, aLine, false, false);
    featurizePhrases(ex, sent, pLine);
    featurizeSentence(ex, sent);
  }

  private static void featurizePhrases(PhraseExtractor ex,
      SymmetricalWordAlignment sent, String pLine) {
    if (pLine != null)
      ex.setSentenceInfo(sent, pLine);
    ex.extractPhrases(sent);
  }

  private void featurizeSentence(AbstractPhraseExtractor ex,
      SymmetricalWordAlignment sent) {
    for (int i = 0; i < extractors.size(); i++) {
      AbstractFeatureExtractor e = extractors.get(i);
      String infoLine = (nThreads == 0) ? infoLinesForExtractors.get(i) : "";
      e.featurizeSentence(sent, infoLine, ex.getAlGrid());
    }
  }

  // Write combined features to a stream.
  boolean write(PrintStream oStream, boolean withAlign) {
    if (oStream == null)
      oStream = System.out;
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    System.err.printf("Phrases in memory: %d\n", alTemps.size());
    int phrasesWritten = 0;

    for (int idx = 0; idx < alTemps.size(); ++idx) {
      boolean skip = false;
      StringBuilder str = new StringBuilder();

      if (idx % 10000 == 0 || idx + 1 == alTemps.size()) {
        long totalMemory = Runtime.getRuntime().totalMemory() / (1 << 20);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1 << 20);
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis) / 1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        if (verbose)
          System.err
              .printf(
                  "writing phrase %d (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                  idx, totalStepSecs, totalMemory, freeMemory);
      }

      if (!alTemps.reconstructAlignmentTemplate(alTemp, idx))
        continue;
      str.append(phrasePrinter.toString(alTemp, withAlign));
      str.append(AlignmentTemplate.DELIM);

      for (AbstractFeatureExtractor e : extractors) {
        Object scores = e.score(alTemp);

        if (scores == null) {
          skip = true;
          break;
        }

        if (scores instanceof float[]) { // as dense vector
          float[] scoreArray = (float[]) scores;
          for (float aScoreArray : scoreArray) {
            str.append(aScoreArray).append(" ");
          }
        } else if (scores instanceof double[]) {
          double[] scoreArray = (double[]) scores;
          for (double aScoreArray : scoreArray) {
            str.append((float) aScoreArray).append(" ");
          }
        } else if (scores.getClass().equals(Int2IntLinkedOpenHashMap.class)) { // as
                                                                               // sparse
                                                                               // vector
          Int2IntLinkedOpenHashMap counter = (Int2IntLinkedOpenHashMap) scores;
          for (int fIdx : counter.keySet()) {
            int cnt = counter.get(fIdx);
            int minCount = 1;
            if (cnt >= minCount) {
              str.append(printFeatureNames ? featureIndex.get(fIdx) : fIdx);
              str.append("=").append(cnt).append(" ");
            }
          }
        } else {
          throw new UnsupportedOperationException(
              "AbstractFeatureExtractor should return double[] or Counter, not "
                  + scores.getClass());
        }
      }
      if (!skip) {
        oStream.println(str.toString());
        ++phrasesWritten;
      }
    }

    System.err.printf("Phrases written: %d\n", phrasesWritten);
    double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    System.err.printf("Done generating phrase table. Seconds: %.3f.\n",
        totalTimeSecs);
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

    PrintStream oStream = IOTools.getWriterFromFile(outputFile);

    if (filterFromDev) {
      int sz = sourceFilter.size();
      int size = 1 + (numSplits == 0 ? sz : sz / numSplits);
      int startLine = 0;
      while (startLine < sz) {
        init();
        sourceFilter.setRange(startLine, startLine + size);
        extractFromAlignedData();
        write(oStream, withAlign);
        startLine += size;
      }
    } else {
      init();
      extractFromAlignedData();
      write(oStream, withAlign);
    }

    if (oStream != null)
      oStream.close();

  }

  static void usage() {
    System.err
        .print("Usage: java edu.stanford.nlp.mt.train.PhraseExtract [ARGS]\n"
            + "Sets of mandatory arguments (user must select either set 1, 2, or 3):\n"
            + "Set 1:\n"
            + " -fCorpus <file> : source-language corpus\n"
            + " -eCorpus <file> : target-language corpus\n"
            + " -align <file> : alignment file (Moses format)\n"
            + "Set 2:\n"
            + " -fCorpus <file> : source-language corpus\n"
            + " -eCorpus <file> : target-language corpus\n"
            + " -feAlign <file> : f-e alignment file (GIZA format)\n"
            + " -efAlign <file> : e-f alignment file (GIZA format)\n"
            + "Set 3:\n"
            + " -inputDir <directory> : alignment directory created by Berkeley aligner v2.1\n"
            + "Optional arguments:\n"
            + " -outputFile <file> : phrases are written to this file\n"
            + " -symmetrization <type> : alignment symmetrization heuristic (expects -feAlign and -efAlign)\n"
            + " -extractors <class1>[:<class2>:...:<classN>] : feature extractors\n"
            + " -fFilterCorpus <file> : filter against a specific dev/test set\n"
            + " -fFilterList <file> : phrase extraction restricted to this list\n"
            + " -split <N> : split filter list into N chunks\n"
            + "  (divides memory usage by N, but multiplies running time by N)\n"
            + " -refFile <file> : check features against a Moses phrase table\n"
            + " -maxLen <n> : max phrase length\n"
            + " -maxLenF <n> : max phrase length (source-language)\n"
            + " -maxLenE <n> : max phrase length (target-language)\n"
            + " -numLines <n> : number of lines to process (<0 : all)\n"
            + " -startAtLine <n> : start at line <n> (<0 : all)\n"
            + " -endAtLine <n> : end at line <n> (<0 : all)\n"
            + " -noAlign : do not specify alignment in phrase table\n"
            + " -verbose : enable verbose mode\n");
  }

  public static void main(String[] args) throws IOException {

    if (args.length == 1 && args[0].equals("-help")) {
      usage();
      return;
    }

    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    System.err.printf("Extraction started at %s on %s.\n",
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
