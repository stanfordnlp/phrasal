package mt.train;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IString;

import java.util.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;

import mt.base.IOTools;
import mt.base.Sequence;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

/**
 * Combines multiple phrase-level feature extractors into one,
 * and prints their outputs to stdout.
 *
 * @author Michel Galley
 */
public class CombinedFeatureExtractor {

  static public final String CONFIG_OPT = "config";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String EXTRACTORS_OPT = "extractors";
  
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus";
  static public final String EMPTY_FILTER_LIST_OPT = "fEmptyFilterList";
  static public final String FILTER_LIST_OPT = "fFilterList";
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
  static public final String ADD_BOUNDARY_MARKERS_OPT = "addSentenceBoundaryMarkers";

  // phrase translation probs:
  static public final String EXACT_PHI_OPT = "exactPhiCounts";
  static public final String IBM_LEX_MODEL_OPT = "ibmLexModel";
  static public final String ONLY_ML_OPT = "onlyML";
  static public final String PTABLE_PHI_FILTER_OPT = "phiFilter"; // p_phi(e|f) filtering
  static public final String PTABLE_LEX_FILTER_OPT = "lexFilter"; // p_lex(e|f) filtering

  // lexicalized re-ordering models:
  static public final String LEX_REORDERING_TYPE_OPT = "pharaohLexicalizedModel";
  static public final String LEX_REORDERING_PHRASAL_OPT = "phrasalLexicalizedModel";
  static public final String LEX_REORDERING_START_CLASS_OPT = "lexicalizedModelHasStart";
  static public final String LEX_REORDERING_2DISC_CLASS_OPT = "lexicalizedModelHas2Disc";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(new String[] { 
       F_CORPUS_OPT, E_CORPUS_OPT, A_CORPUS_OPT, EXTRACTORS_OPT 
     }));
    OPTIONAL_OPTS.addAll(Arrays.asList(new String[] { 
       FILTER_CORPUS_OPT, EMPTY_FILTER_LIST_OPT, FILTER_LIST_OPT, REF_PTABLE_OPT, 
       SPLIT_SIZE_OPT, OUTPUT_FILE_OPT, NO_ALIGN_OPT, 
       AbstractPhraseExtractor.MAX_PHRASE_LEN_OPT, 
       AbstractPhraseExtractor.MAX_PHRASE_LEN_E_OPT, 
       AbstractPhraseExtractor.MAX_PHRASE_LEN_F_OPT, 
       AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_OPT, 
       AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_E_OPT, 
       AbstractPhraseExtractor.MAX_EXTRACTED_PHRASE_LEN_F_OPT, 
       NUM_LINES_OPT, PRINT_FEATURE_NAMES_OPT, MIN_COUNT_OPT,
       START_AT_LINE_OPT, END_AT_LINE_OPT, MAX_FERTILITY_OPT,
       EXACT_PHI_OPT, IBM_LEX_MODEL_OPT, ONLY_ML_OPT,
       PTABLE_PHI_FILTER_OPT, PTABLE_LEX_FILTER_OPT,
       LEX_REORDERING_TYPE_OPT, LEX_REORDERING_PHRASAL_OPT,
       LEX_REORDERING_START_CLASS_OPT, LEX_REORDERING_2DISC_CLASS_OPT,
       ADD_BOUNDARY_MARKERS_OPT
     }));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }
  
  public static final String DEBUG_PROPERTY = "DebugCombinedFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugCombinedFeatureExtractor";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  private static BshInterpreter interpreter = new BshInterpreter();
  private static int minCount = 1;

  protected List<AbstractFeatureExtractor> extractors;
  // each extract is allowed to have one file that contains extra information (one line per sentence)
  private List<String> infoFileForExtractors;
  private List<String> infoLinesForExtractors;
  private AbstractPhraseExtractor phraseExtractor = null;
  private PharaohFeatureExtractor mosesExtractor = null;

  protected AlignmentTemplates alTemps;
  protected AlignmentTemplateInstance alTemp;
  protected Index<String> featureIndex = new Index<String>();

  private Properties prop;
  private int startAtLine = -1, endAtLine = -1, numSplits = 0;
  private String fCorpus, eCorpus, align, outputFile;
  private boolean filterFromDev = false, printFeatureNames = true, noAlign;
  Sequence<IString>[] fPhrases;

  // Number of passes over training data needed:
  private int passNumber = 0;
  private int totalPassNumber = 1;

  @SuppressWarnings("unchecked")
  public CombinedFeatureExtractor(Properties prop) throws IOException {
    analyzeProperties(prop);
  }

  @SuppressWarnings("unchecked")
  public void analyzeProperties(Properties prop) throws IOException {

    this.prop = prop;

    // Possibly load properties from config file:
    String configFile = prop.getProperty(CONFIG_OPT);
    if(configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch(IOException e) {
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }

    // Check required, optional properties:
    System.err.println("properties: "+prop.toString());
    if(!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      System.err.printf
       ("The following required fields are missing: %s\n", missingFields);
      usage();
      System.exit(1);
    }
    
    if(!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set extraFields = new HashSet(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      System.err.printf
       ("The following fields are unrecognized: %s\n", extraFields);
      usage();
      System.exit(1);
    }
    
    // Analyze props:
    // Mandatory arguments:
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);
    align = prop.getProperty(A_CORPUS_OPT);
    
    // Phrase filtering arguments:
    String fFilterCorpus = prop.getProperty(FILTER_CORPUS_OPT);
    String fFilterList = prop.getProperty(FILTER_LIST_OPT);
    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(ADD_BOUNDARY_MARKERS_OPT,"false"));
    boolean emptyFilterList =
      Boolean.parseBoolean(prop.getProperty(EMPTY_FILTER_LIST_OPT,"false"));
    numSplits = Integer.parseInt(prop.getProperty(SPLIT_SIZE_OPT,"0"));
    fPhrases = null;
    if(emptyFilterList || fFilterList != null || fFilterCorpus != null)
      filterFromDev = true;
    if(fFilterList != null)
      fPhrases = SourceFilteringToolkit.getPhrasesFromList(fFilterList);
    else if(fFilterCorpus != null)
      fPhrases = SourceFilteringToolkit.getPhrasesFromFilterCorpus
        (fFilterCorpus, AbstractPhraseExtractor.maxPhraseLenF,addBoundaryMarkers);
    
    // Other optional arguments:
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT,"-1"));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT,"-2"))+1;
    printFeatureNames = Boolean.parseBoolean(prop.getProperty(PRINT_FEATURE_NAMES_OPT,"true"));
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT,"-1"));
    if(numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    noAlign = Boolean.parseBoolean(prop.getProperty(NO_ALIGN_OPT,"false"));
    outputFile = prop.getProperty(OUTPUT_FILE_OPT);
  }

  @SuppressWarnings("unchecked")
  public void init() {
    String exsString = prop.getProperty(EXTRACTORS_OPT);
    if(exsString.equals("moses"))
      exsString = "mt.train.PharaohFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor";
    alTemps = new AlignmentTemplates(prop, filterFromDev);
    alTemp = new AlignmentTemplateInstance();
    extractors = new ArrayList<AbstractFeatureExtractor>();
    infoFileForExtractors = new ArrayList<String>();

    for(String exStr : exsString.split(":")) {
      try {
        AbstractFeatureExtractor fe = null;
        String[] extractorAndInfofile = exStr.split("=");
        String infoFilename = null;

        // if the extractor string contains "=", then assume 
        // that A in A=B is the extractor class name, and 
        // B is an "info" file with same number of lines as sentence pairs.
        if (extractorAndInfofile.length==2) {
          infoFilename = extractorAndInfofile[1];
          exStr = extractorAndInfofile[0];
        } else if (extractorAndInfofile.length!=1) {
          throw new RuntimeException("extractor argument format error");
        }
        
        // if exStr contains parentheses, assume it is a call to a constructor 
        // (without the "new"):
        int pos = exStr.indexOf('(');
        if(pos >= 0) {
          StringBuffer constructor = new StringBuffer("new ").append(exStr);
          System.err.println("Running constructor: "+constructor);
          fe = (AbstractFeatureExtractor) interpreter.eval(constructor.toString());
        } else {
          Class cls = Class.forName(exStr);
          Constructor ct = cls.getConstructor(new Class[] {});
          fe = (AbstractFeatureExtractor) ct.newInstance(new Object[] {});
          if(fe instanceof PharaohFeatureExtractor) {
            mosesExtractor = (PharaohFeatureExtractor) fe;
          }
        }

        fe.init(prop, featureIndex, alTemps);
        extractors.add(fe);
        infoFileForExtractors.add(infoFilename);
        System.err.println("New class instance: "+fe.getClass());

      } catch (Exception e) {
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }
    phraseExtractor = new LinearTimePhraseExtractor(prop,alTemps,extractors);
    setTotalPassNumber();
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language phrases.
   * @param list Extract features only for this phrase list.
   * @param start Start index into list.
   * @param start End index into list.
   */
  public void restrictExtractionTo(Sequence<IString>[] list, int start, int end) {
    assert(filterFromDev);

    if(end < Integer.MAX_VALUE)
      System.err.printf("Filtering against phrases: %d-%d\n", start, end-1);

    for(int i=start; i<end && i<list.length; ++i) {
      Sequence<IString> f = list[i];
      alTemps.addForeignPhraseToIndex(f);
    }
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language phrases.
   * @param list Extract features only for this phrase list.
   */
  public void restrictExtractionTo(Sequence<IString>[] list) {
    assert(filterFromDev);  
    restrictExtractionTo(list,0,Integer.MAX_VALUE);
  }

  /**
   * Make as many passes over training data as needed to extract features. 
   *
   * @param fCorpus
   * @param eCorpus
   * @param aCorpus
   */
  public void extractFromAlignedData(String fCorpus, String eCorpus, String aCorpus) {

    boolean addBoundaryMarkers = Boolean.parseBoolean(prop.getProperty(ADD_BOUNDARY_MARKERS_OPT,"false"));

    if(!filterFromDev)
      System.err.println("WARNING: extracting phrase table not targeted to a specific dev/test corpus!");
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    SymmetricalWordAlignment sent = new SymmetricalWordAlignment();

    try {
      for(passNumber=0; passNumber<totalPassNumber; ++passNumber) {
        alTemps.enableAlignmentCounts(passNumber == 0);

        // Set current pass:
        for(AbstractFeatureExtractor e : extractors)
          e.setCurrentPass(passNumber);

        // Read data and process data:
        System.err.printf("Pass %d on training data (max phrase len: %d,%d)...\n",
          passNumber+1, AbstractPhraseExtractor.maxPhraseLenF, AbstractPhraseExtractor.maxPhraseLenE);
        LineNumberReader
          fReader = IOTools.getReaderFromFile(fCorpus),
          eReader = IOTools.getReaderFromFile(eCorpus),
          aReader = IOTools.getReaderFromFile(aCorpus);

        // make Readers from the info files for each extractors
        List<LineNumberReader>
          infoReaders = new ArrayList<LineNumberReader>();
        for(String infoFile : infoFileForExtractors) {
          LineNumberReader r = null;
          if (infoFile != null) {
            r = IOTools.getReaderFromFile(infoFile);
          }
          infoReaders.add(r);
        }
        
        int lineNb=0;
        for (String fLine;; ++lineNb) {
          fLine = fReader.readLine();
          boolean done = (fLine == null || lineNb == endAtLine);

          if(lineNb % 1000 == 0 || done) {
            long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
            double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
            startStepTimeMillis = System.currentTimeMillis();
            System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
                              lineNb, totalStepSecs, totalMemory, freeMemory, alTemps.getSizeInfo());
          }

          if(done) {
            if(startAtLine >= 0 || endAtLine >= 0)
              System.err.printf("Range done: [%d-%d], current line is %d.\n",
                                startAtLine, endAtLine-1, lineNb);
            break;
          }

          String eLine = eReader.readLine();
          if(eLine == null)
            throw new IOException("Target-language corpus is too short!");
          String aLine = aReader.readLine();
          if(aLine == null)
            throw new IOException("Alignment file is too short!");
          if(aLine.equals(""))
            continue;

          infoLinesForExtractors = new ArrayList<String>();
          for (LineNumberReader infoReader : infoReaders) {
            String infoLine = null;
            if (infoReader != null) {
              infoLine = infoReader.readLine();
              if(infoLine == null)
                throw new IOException("Info file for one extractor is too short!");
            }
            infoLinesForExtractors.add(infoLine);
          }
          
          if(lineNb < startAtLine)
            continue;
          if(DETAILED_DEBUG) {
            System.err.printf("e(%d): %s\n",lineNb,eLine);
            System.err.printf("f(%d): %s\n",lineNb,fLine);
            System.err.printf("a(%d): %s\n",lineNb,aLine);
          }
          sent.init(lineNb,fLine,eLine,aLine,false,false,addBoundaryMarkers);
          extractPhrasalFeatures(sent);
          extractSententialFeatures(sent);
        }

        if(eReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Target-language corpus contains extra lines!");
        if(aReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
          throw new IOException("Alignment file contains extra lines!");

        fReader.close();
        eReader.close();
        aReader.close();

        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
        System.err.printf("Done with pass %d. Seconds: %.3f.\n", passNumber+1, totalTimeSecs);
      }

      // just let each extractor output some stuff to the STDERR
      for(AbstractFeatureExtractor e : extractors)
        e.report();

    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void extractPhrasalFeatures(SymmetricalWordAlignment sent) {
    phraseExtractor.extractPhrases(sent);
  }

  private void extractSententialFeatures(SymmetricalWordAlignment sent) {
    for(int i = 0; i < extractors.size(); i++) {
      AbstractFeatureExtractor e = extractors.get(i);
      String infoLine = infoLinesForExtractors.get(i);
      e.extract(sent,infoLine,phraseExtractor.getAlGrid());
    }
  }

  /**
   * Write combined features to a stream.
   */
  public boolean write(PrintStream oStream, boolean noAlign) {
    if(oStream == null)
        oStream = System.out;
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    System.err.printf("Alignment templates to write: %d\n",alTemps.size());

    for(int idx=0; idx<alTemps.size(); ++idx) {
      boolean skip=false;
      StringBuilder str = new StringBuilder();

      if(idx % 10000 == 0 || idx+1==alTemps.size()) {
        long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
        long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        System.err.printf("writing phrase %d (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                          idx, totalStepSecs, totalMemory, freeMemory);
      }

      alTemps.reconstructAlignmentTemplate(alTemp, idx);
      str.append(alTemp.toString(noAlign));
      str.append(AlignmentTemplate.DELIM);

      for(AbstractFeatureExtractor e : extractors) {
        Object scores = e.score(alTemp);

        if(scores == null) {
          skip=true;
          break;
        }

        if(scores.getClass().isArray()) { // as dense vector
          double[] scoreArray = (double[]) scores;
          for(int i=0; i<scoreArray.length; ++i) {
            str.append((float)scoreArray[i]).append(" ");
          }
        } else if(scores.getClass().equals(Int2IntLinkedOpenHashMap.class)) { // as sparse vector
          Int2IntLinkedOpenHashMap counter = (Int2IntLinkedOpenHashMap) scores;
          for(int fIdx : counter.keySet()) {
            int cnt = counter.get(fIdx);
            if(cnt >= minCount) {
              str.append(printFeatureNames ? featureIndex.get(fIdx) : fIdx);
              str.append("=").append(cnt).append(" ");
            }
          }
        } else {
          throw new UnsupportedOperationException
            ("AbstractFeatureExtractor should return double[] or Counter, not "+scores.getClass());
        }
      }
      if(!skip)
        oStream.println(str.toString());
    }

    double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("Done with writing phrase table. Seconds: %.3f.\n", totalTimeSecs);
    return true;
  }

  private void setTotalPassNumber() {
    totalPassNumber = 0;
    for(AbstractFeatureExtractor ex : extractors) {
      int p = ex.getRequiredPassNumber();
      if(p > totalPassNumber)
        totalPassNumber = p;
    }
  }

  public void extractAll() {
    // Split filter list into N chunks:
    if(numSplits > 1) {

      if(!filterFromDev)
        throw new RuntimeException("-"+SPLIT_SIZE_OPT+" argument only possible with -"+FILTER_CORPUS_OPT+", -"+FILTER_LIST_OPT+".");

      PrintStream oStream = IOTools.getWriterFromFile(outputFile);
      int size = fPhrases.length/numSplits+1;
      int startLine = 0;

      while(startLine < fPhrases.length) {
        init();
        restrictExtractionTo(fPhrases, startLine, startLine+size);
        extractFromAlignedData(fCorpus, eCorpus, align);
        write(oStream, noAlign);
        startLine += size;
      }

      if(oStream != null)
        oStream.close();
    } 
    // Only one chunk at a time (more advanced features available here):
    else {

      init();
      
      // Various filtering options:
      if(fPhrases != null)
        restrictExtractionTo(fPhrases);
      extractFromAlignedData(fCorpus, eCorpus, align);

      // Check phrase table against existing one:
      String refFile = prop.getProperty(REF_PTABLE_OPT);
      if(refFile != null && mosesExtractor != null) {
        try {
          BufferedReader refReader = IOTools.getReaderFromFile(refFile);
          mosesExtractor.checkAgainst(refReader);
          refReader.close();
        } catch(IOException e) {
          e.printStackTrace();
        }
      }
      
      System.err.println("saving features to: "+outputFile);
      PrintStream oStream = IOTools.getWriterFromFile(outputFile);
      write(oStream, noAlign);
      if(oStream != null)
        oStream.close();
    }
  }

  static void usage() {
    System.err.print
    ("Usage: java CombinedFeatureExtractor [ARGS]\n"+
     "Mandatory arguments:\n"+
     " -fCorpus <file> : source-language corpus\n"+
     " -eCorpus <file> : target-language corpus\n"+
     " -align <file> : alignment file\n"+
     " -extractors <class1>[:<class2>:...:<classN>]\n"+
     "Optional arguments:\n"+
     " -outputFile <file>\n"+
     " -fFilterCorpus <file> : filter against a specific dev/test set\n"+
     " -fFilterList <file> : phrase extraction restricted to this list\n"+
     " -split <N> : split filter list into N chunks\n"+
     "  (divides memory usage by N, but multiplies running time by N)\n"+
     " -refFile <file> : check features against a Moses phrase table\n"+
     " -maxLen <n> : max phrase length\n"+
     " -maxLenF <n> : max phrase length (source-language)\n"+
     " -maxLenE <n> : max phrase length (target-language)\n"+
     " -numLines <n> : number of lines to process (<0 : all)\n"+
     " -startAtLine <n> : start at line <n> (<0 : all)\n"+
     " -endAtLine <n> : end at line <n> (<0 : all)\n"+
     " -noAlign : do not write alignment to stdout\n");
  }

  public static void main(String[] args) throws IOException {

    Properties prop = StringUtils.argsToProperties(args);
    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    System.err.println("extraction started at: "+formatter.format(new Date()));

    try {
      CombinedFeatureExtractor e = new CombinedFeatureExtractor(prop);
      e.extractAll();
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }
    
    System.err.println("extraction ended at: "+formatter.format(new Date()));
  }
}
