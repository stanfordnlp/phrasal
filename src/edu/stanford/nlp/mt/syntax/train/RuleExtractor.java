package edu.stanford.nlp.mt.syntax.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.*;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.util.StringUtils;

/**
 * Main class for performing GHKM rule extraction.
 * 
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class RuleExtractor {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  
  // Mandatory arguments:
  static public final String CONFIG_OPT = "config";
  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String E_PARSED_CORPUS_OPT = "eParsedCorpus";
  static public final String A_CORPUS_OPT = "align";

  // GHKM rule extraction class:
  // default: edu.stanford.nlp.mt.syntax.train.RelativeFrequencyFeatureExtractor
  static public final String EXTRACTORS_OPT = "extractors"; 

  // Rule filtering:
  static public final String MAX_COMPOSITIONS_OPT = "maxCompositions";
  static public final String MAX_LHS_SIZE_OPT = "maxLHS";
  static public final String MAX_RHS_SIZE_OPT = "maxRHS";
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus"; // unigram filtering.

  // I/O:
  static public final String NUM_LINES_OPT = "numLines";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String JOSHUA_FORMAT_OPT = "joshuaFormat";
  public static final String ONE_INDEXED_ALIGNMENT_OPT = "oneIndexedAlignment";
  public static final String REVERSED_ALIGNMENT_OPT = "reversedAlignment";
  public static final String SAVE_PREFIX_OPT = "savePrefix";

  // Misc:
  static public final String THREADS_OPT = "threads";
  static public final String NO_PRINT_OPT = "noPrint";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList
        (F_CORPUS_OPT, E_PARSED_CORPUS_OPT, A_CORPUS_OPT));
    OPTIONAL_OPTS.addAll(Arrays.asList
        (FILTER_CORPUS_OPT, NUM_LINES_OPT,
         START_AT_LINE_OPT, END_AT_LINE_OPT,
         EXTRACTORS_OPT, MAX_COMPOSITIONS_OPT,
         MAX_LHS_SIZE_OPT, MAX_RHS_SIZE_OPT, E_CORPUS_OPT,
            JOSHUA_FORMAT_OPT, THREADS_OPT, NO_PRINT_OPT,
         Rule.MAX_UNALIGNED_RHS_OPT, REVERSED_ALIGNMENT_OPT, 
         ONE_INDEXED_ALIGNMENT_OPT, SAVE_PREFIX_OPT));
     ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
     ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  final String fCorpus, eCorpus, aCorpus, eParsedCorpus;
  final AlignmentGraph ag = new AlignmentGraph();
  final RuleIndex ruleIndex = new RuleIndex();
  final StringNumberer numberer = new StringNumberer();
  final List<FeatureExtractor> extractors =  new ArrayList<FeatureExtractor>();
  final int maxLHS, maxRHS;
  final boolean joshuaFormat, noPrint;
  final String savePrefix;
  int startAtLine, endAtLine, nThreads;
  UnigramRuleFilter filter = null;

  private final List<Thread> threads = new LinkedList<Thread>();
  private final LinkedBlockingQueue<String[]> dataQueue = new LinkedBlockingQueue<String[]>(1000);
  boolean doneReadingData;

  public RuleExtractor(Properties prop) throws ClassNotFoundException, IllegalAccessException, InstantiationException {

    // Mandatory arguments:
    aCorpus = prop.getProperty(A_CORPUS_OPT);
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);
    eParsedCorpus = prop.getProperty(E_PARSED_CORPUS_OPT);

    // Optional arguments:
    String extractorStr = prop.getProperty(EXTRACTORS_OPT,
         edu.stanford.nlp.mt.syntax.train.RelativeFrequencyFeatureExtractor.class.getName());
    String[] extractorNames = extractorStr.split(":");
    int maxCompositions = Integer.parseInt(prop.getProperty(MAX_COMPOSITIONS_OPT,"0"));
    AlignmentGraph.setMaxCompositions(maxCompositions);
    maxLHS = Integer.parseInt(prop.getProperty(MAX_LHS_SIZE_OPT, "15"));
    maxRHS = Integer.parseInt(prop.getProperty(MAX_RHS_SIZE_OPT, "10"));
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT,Integer.toString(0)));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT,Integer.toString(Integer.MAX_VALUE)));
    savePrefix = prop.getProperty(SAVE_PREFIX_OPT,"out");
    nThreads = Integer.parseInt(prop.getProperty(THREADS_OPT,"0"));

    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT,"-1"));
    if (numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }

    if (prop.getProperty(FILTER_CORPUS_OPT) != null)
      filter = new UnigramRuleFilter(prop.getProperty(FILTER_CORPUS_OPT));
    if (prop.getProperty(Rule.MAX_UNALIGNED_RHS_OPT) != null)
      Rule.MAX_UNALIGNED_RHS = Integer.parseInt(prop.getProperty(Rule.MAX_UNALIGNED_RHS_OPT));
    joshuaFormat = Boolean.parseBoolean(prop.getProperty(JOSHUA_FORMAT_OPT,"false"));
    noPrint = Boolean.parseBoolean(prop.getProperty(NO_PRINT_OPT,"false"));

    // Initialize extractors:
    for (String extractorName : extractorNames) {
      FeatureExtractor extractor = (FeatureExtractor) Class.forName(extractorName).newInstance();
      extractors.add(extractor);
      extractor.init(ruleIndex,prop);
    }
    ag.setFeatureExtractors(extractors);
  }

  private boolean doneReadingData() { return doneReadingData; }

  class Extractor extends Thread {

    final RuleExtractor ex;
    final LinkedBlockingQueue<String[]> dataQueue;
    final AlignmentGraph ag = new AlignmentGraph();
    final StringNumberer numberer = new StringNumberer();

    Extractor(RuleExtractor ex, AlignmentGraph a, LinkedBlockingQueue<String[]> q) {
      this.ex = ex;
      dataQueue = q;
      ag.setFeatureExtractors(a.extractors);
    }

    public void run() {
      System.err.printf("Starting thread %s...\n", this);
      try {
        while(!dataQueue.isEmpty() || !ex.doneReadingData()) {
          String[] lines = dataQueue.poll();
          if(lines != null) {
            ex.processLine(ag, numberer, lines[0], lines[1], lines[2], lines[3]);
          }
        }
      } catch(IOException e) {
        throw new RuntimeException();
      }
      System.err.printf("Ending thread %s.\n", this);
    }
  }

  public void processLine(AlignmentGraph localAG, StringNumberer numberer, String aLine, String fLine, String eTreeLine, String eLine)
    throws IOException {

    localAG.init(aLine, fLine, eTreeLine, eLine);

    for (Rule r : localAG.extractRules(numberer)) {

      if (filter != null && !filter.keep(r))
        continue;

      if (r.lhsLabels.length > maxLHS || r.rhsLabels.length > maxRHS)
        continue;

      for (FeatureExtractor extractor : extractors)
        extractor.extractFeatures(ruleIndex.getRuleId(r));
    }
  }

  public void extractRules() {

    doneReadingData = false;

    assert(threads.isEmpty());
    assert(dataQueue.isEmpty());
    
    for (int i=0; i<nThreads; ++i) {
      System.err.printf("Creating thread %d...\n", i);
      Extractor thread = new Extractor(this, ag, dataQueue);
      thread.start();
      threads.add(thread);
    }

    LineNumberReader aReader = IOTools.getReaderFromFile(aCorpus);
    LineNumberReader fReader = IOTools.getReaderFromFile(fCorpus);
    LineNumberReader eReader =
      (eCorpus != null) ? IOTools.getReaderFromFile(eCorpus) : null;
    LineNumberReader eTreeReader = IOTools.getReaderFromFile(eParsedCorpus);

    long startStepTimeMillis = System.currentTimeMillis();
    boolean fatalError = false;

    for (int lineNb = 0; !fatalError && lineNb <= endAtLine; ++lineNb) {

      String aLine="", fLine="", eTreeLine, eLine=null;

      try {

        aLine = aReader.readLine();
        fLine = fReader.readLine();
        eTreeLine = eTreeReader.readLine();

        if (eReader != null)
          eLine = eReader.readLine();

        if (lineNb < startAtLine)
          continue;

        boolean skip = false;
        boolean done = false;

        if (eTreeLine == null && fLine == null && aLine == null)
          done=true;

        if (lineNb % 10000 == 0 || done) {
          long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
          long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
          double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
          startStepTimeMillis = System.currentTimeMillis();
          System.err.printf
           ("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
               lineNb, totalStepSecs, totalMemory, freeMemory, ruleIndex.getSizeInfo());
        }

        if (done)
          break;

        if ("".equals(eTreeLine) || "".equals(fLine) || "".equals(aLine)) {
          skip = true;
          if (DEBUG)
            System.err.printf("RuleExtractor: extractRules: empty line at %d\n", lineNb);
        }

        if (eTreeLine == null || fLine == null || aLine == null) {
          throw new IOException("Wrong number of lines at: "+ lineNb);
        }

        if (skip)
          continue;

        if (threads.size() == 0) {
          processLine(ag, numberer, aLine, fLine, eTreeLine, eLine);
        } else {
          dataQueue.put(new String[] {aLine, fLine, eTreeLine, eLine});
        }

      } catch(Exception e) {
        System.err.printf("Exception at lineNb: %d\n", lineNb);
        System.err.printf("RuleExtractor: extractRules: lineNb: %d\n", lineNb);
        System.err.printf("RuleExtractor: extractRules: f: %s\n",fLine);
        System.err.printf("RuleExtractor: extractRules: e: %s\n",eLine);
        System.err.printf("RuleExtractor: extractRules: a: %s\n",aLine);
        throw new RuntimeException(e);
      }
    }

    try {
      aReader.close();
      fReader.close();
      eTreeReader.close();
      if (eReader != null)
        eReader.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    doneReadingData = true;

    try {
      for(int i=0; i<nThreads; ++i)
        threads.get(i).join();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    assert(dataQueue.isEmpty());
    threads.clear();

    System.err.printf("Done with rule extraction (%d rules).\n", ruleIndex.size());

    if (noPrint)
      return;

    System.err.printf("Printing rules:\n");

    int count = -1;

    for (RuleIndex.RuleId rId : ruleIndex) {

      Rule r = rId.rule;

      if (++count % 100000 == 0) {
        long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
        long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        System.err.printf
         ("%d rules printed (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
          count, totalStepSecs, totalMemory, freeMemory);
      }

      // Print rule:
      if (joshuaFormat) {
        System.out.printf("[%s] ||| %s ||| %s |||",
           IString.getString(r.lhsLabels[0]), r.toJoshuaLHS(), r.toJoshuaRHS());
      } else {
        System.out.printf("%s |||",r.toString());
      }

      // Features for each rule:
      for (FeatureExtractor extractor : extractors) {
        for (double s : extractor.score(rId)) {
          System.out.print(" ");
          System.out.print((float)s);
        }
      }

      System.out.println();
    }

    System.err.println("Done with rule extraction.\nSaving features:");
    for(FeatureExtractor extractor : extractors)
      extractor.save(savePrefix);
    System.err.println("Done.");

  }

  public static void usage() {
    System.err.print
    ("Usage: java RuleExtractor [ARGS]\n"+
     "Mandatory arguments:\n"+
     " -fCorpus <file> : source-language corpus\n"+
     " -eCorpus <file> : target-language parsed corpus\n"+
     " -align <file> : alignment file\n"+
     "Optional arguments:\n"+
     " -threads <n>: number of threads (default: 1)\n"+
     " -extractors <class1>[:<class2>:...:<classN>]\n"+
     " -fFilterCorpus <file> : filter against a specific dev/test set\n"+
     " -numLines <n> : number of lines to process (<0 : all)\n"+
     " -startAtLine <n> : start at line <n> (<0 : all)\n"+
     " -endAtLine <n> : end at line <n> (<0 : all)\n"+
     " -maxLHS <n> : maximum size of LHS\n"+
     " -maxRHS <n> : maximum size of RHS\n"+
     " -maxUnalignedRHS <n> : maximum number of unaligned words in RHS\n"+
     " -joshuaFormat: print rules in Hiero format (preserving STSG equivalence)\n"+
     " -hieroFlatFormat: print rules in Hiero format (not preserving STSG equivalent)\n"
    );
  }

  public static void main(String[] args)
      throws IllegalAccessException, InstantiationException, ClassNotFoundException {

    IString.internStrings(false);

    Properties prop = StringUtils.argsToProperties(args);
    String configFile = prop.getProperty(CONFIG_OPT);

    if (configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch(Exception e) {
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }

    System.err.println("Properties: "+prop.toString());

    if (!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      try {
        throw new RuntimeException(String.format
       ("The following required fields are missing: %s\n", missingFields));
      } catch(Exception e) {
        usage();
        throw new UnsupportedOperationException(e);
      }
    }

    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set<Object> extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      try {
        throw new RuntimeException(String.format
         ("The following fields are unrecognized: %s\n", extraFields));
      } catch(Exception e) {
        usage();
        throw new UnsupportedOperationException(e);
      }
    }

    AlignmentGraph.reversedAlignment 
     = Boolean.parseBoolean(prop.getProperty(REVERSED_ALIGNMENT_OPT,"true"));
    
    AlignmentGraph.oneIndexedAlignment 
     = Boolean.parseBoolean(prop.getProperty(ONE_INDEXED_ALIGNMENT_OPT,"false"));
    
    new RuleExtractor(prop).extractRules();
  }
}
