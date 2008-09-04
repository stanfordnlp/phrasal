package mt.train;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IString;

import java.util.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import mt.base.IOTools;
import mt.base.Sequence;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;

/**
 * Combines multiple phrase-level feature extractors into one,
 * and prints their outputs to stdout; threaded version.
 *
 * @author Michel Galley
 */
public class ThreadedFeatureExtractor {

  static public final String CONFIG_OPT = "config";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String EXTRACTORS_OPT = "extractors";
  static public final String THREAD_OPT = "threads";

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

  @SuppressWarnings("unchecked")
	static final Set<Class> THREAD_SAFE_EXTRACTORS = new HashSet<Class>();

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
       THREAD_OPT
     }));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
    THREAD_SAFE_EXTRACTORS.addAll(Arrays.asList(new Class[] {
         mt.train.LexicalReorderingFeatureExtractor.class,
         mt.train.ExperimentalLexicalReorderingFeatureExtractor.class,
         mt.train.PhiFeatureExtractor.class
         // TODO: mt.train.PharaohFeatureExtractor.class
    }));
  }
  
  public static final String DEBUG_PROPERTY = "DebugThreadedFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugThreadedFeatureExtractor";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  public static final String MAX_QUEUE_SIZE_PROPERTY = "MaxQueueSize";
  public static final int MAX_QUEUE_SIZE = Integer.parseInt(System.getProperty(MAX_QUEUE_SIZE_PROPERTY, "10"));

  private static int minCount = 1;
  private static int numThreads = 0;

  private BlockingQueue<SymmetricalWordAlignment> sentQueue 
   = new SynchronousQueue<SymmetricalWordAlignment>();
  private List<AbstractFeatureExtractor> extractors;
  private AlignmentTemplates alTemps;
  private Index<String> featureIndex = new Index<String>();
  private Properties prop;
  private int startAtLine = -1, endAtLine = -1, numSplits = 0;
  private String fCorpus, eCorpus, align, outputFile;
  private boolean filterFromDev = false, printFeatureNames = true, noAlign;
  private Sequence<IString>[] fPhrases;
  private int totalPassNumber = 1;
  private boolean passDone = false;

  @SuppressWarnings("unchecked")
  public ThreadedFeatureExtractor(Properties prop) throws IOException {
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
    boolean emptyFilterList =
      Boolean.parseBoolean(prop.getProperty(EMPTY_FILTER_LIST_OPT,"false"));
    numSplits = Integer.parseInt(prop.getProperty(SPLIT_SIZE_OPT,"1"));
    fPhrases = null;
    if(emptyFilterList || fFilterList != null || fFilterCorpus != null)
      filterFromDev = true;
    if(fFilterList != null)
      fPhrases = SourceFilteringToolkit.getPhrasesFromList(fFilterList);
    else if(fFilterCorpus != null)
      fPhrases = SourceFilteringToolkit.getPhrasesFromFilterCorpus
        (fFilterCorpus, AbstractPhraseExtractor.maxPhraseLenF);
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
    // Number of threads:
    numThreads = Integer.parseInt(prop.getProperty(THREAD_OPT,"1"));
  }

  @SuppressWarnings("unchecked")
  public void init() {
    BshInterpreter interpreter = new BshInterpreter();
    String exsString = prop.getProperty(EXTRACTORS_OPT);
    if(exsString.equals("moses"))
      exsString = "mt.train.PharaohFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor";
    alTemps = new AlignmentTemplates(prop, filterFromDev);
    extractors = new ArrayList<AbstractFeatureExtractor>();

    for(String exStr : exsString.split(":")) {
      try {
        AbstractFeatureExtractor fe = null;
        // if exStr contains parentheses, assume it is a call to a constructor 
        // (without the "new"):
        int pos = exStr.indexOf('(');
        if(pos >= 0) {
          StringBuffer constructor = new StringBuffer("new ").append(exStr);
          System.err.println("Running constructor: "+constructor);
          fe = (AbstractFeatureExtractor) interpreter.eval(constructor.toString());
        } else {
          Class cls = Class.forName(exStr);
          if(!THREAD_SAFE_EXTRACTORS.contains(cls))
            throw new RuntimeException("Extractor is not thread safe: "+cls);
          Constructor ct = cls.getConstructor(new Class[] {});
          fe = (AbstractFeatureExtractor) ct.newInstance(new Object[] {});
        }
        fe.init(prop, featureIndex, alTemps);
        extractors.add(fe);
        System.err.println("New class instance: "+fe.getClass());
      } catch (Exception e) {
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }
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
    if(!filterFromDev)
      System.err.println("WARNING: extracting phrase table not targeted to a specific dev/test corpus!");
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    passDone = false;
    int passNumber = 0;

    System.err.println("Starting new threads: "+numThreads);
    List<Thread> ts = new ArrayList<Thread>();
    for(int i=0; i<numThreads; ++i) {
      Thread t = new Thread() {
        public void run() {
          AbstractPhraseExtractor phraseExtractor = new LinearTimePhraseExtractor(prop, alTemps, extractors);
          System.err.println("Starting thread: "+this);
          while(!passDone) extractFromNextSentence(phraseExtractor);
        }
      };
      t.start();
      ts.add(t);
    }

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

          if(lineNb < startAtLine)
            continue;
          if(DETAILED_DEBUG) {
            System.err.printf("e(%d): %s\n",lineNb,eLine);
            System.err.printf("f(%d): %s\n",lineNb,fLine);
            System.err.printf("a(%d): %s\n",lineNb,aLine);
          }
          SymmetricalWordAlignment sent = new SymmetricalWordAlignment();
          sent.init(lineNb,fLine,eLine,aLine);
          try { sentQueue.put(sent); } catch(InterruptedException e) {}
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
      passDone = true;

     // Wait until all threads are done:
      for(Thread t : ts) {
        System.err.printf("Waiting for thread %s to terminate...\n",t);
        try { t.join(); } catch(InterruptedException e) {}
        System.err.printf("Thread %s terminated.\n",t);
      }

      // just let each extractor output some stuff to the STDERR
      for(AbstractFeatureExtractor e : extractors)
        e.report();

    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * @return true if thread calling this method should stop running.
   */
  public void extractFromNextSentence(AbstractPhraseExtractor phraseExtractor) {
    SymmetricalWordAlignment sent = null;
    try { sent = sentQueue.poll(1L, java.util.concurrent.TimeUnit.SECONDS); } catch(InterruptedException e) {}
    if(sent != null) {
      phraseExtractor.extractPhrases(sent);
      for(int i = 0; i < extractors.size(); i++) {
        AbstractFeatureExtractor e = extractors.get(i);
        AlignmentGrid alGrid = phraseExtractor.getAlGrid();
        synchronized(e) { e.extract(sent,null,alGrid); }
      }
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

    AlignmentTemplateInstance alTemp = new AlignmentTemplateInstance();

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

  static void usage() {
    System.err.print
    ("Usage: java ThreadedFeatureExtractor [ARGS]\n"+
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
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd GGG hh:mm aaa");
    System.err.println("extraction started at: "+formatter.format(new Date()));
    try {
      ThreadedFeatureExtractor e = new ThreadedFeatureExtractor(prop);
      e.extractAll();
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }
    System.err.println("extraction ended at: "+formatter.format(new Date()));
  }
}
