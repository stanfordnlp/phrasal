package edu.stanford.nlp.mt.syntax.train;

import edu.stanford.nlp.mt.base.IOTools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;

import edu.stanford.nlp.util.StringUtils;

/**
 * @author Michel Galley
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

  // Type of training:
  public static final String TRAIN_MARKOV_OPT = "model";

  // GHKM rule extraction class:
  static public final String EXTRACTORS_OPT = "extractors"; // default: edu.stanford.nlp.mt.syntax.train.RelativeFrequencyFeatureExtractor

  // Rule filtering:
  static public final String MAX_COMPOSITIONS_OPT = "maxCompositions";
  static public final String MAX_LHS_SIZE_OPT = "maxLHS";
  static public final String MAX_RHS_SIZE_OPT = "maxRHS";
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus"; // unigram filtering. TODO: more elaborate filtering

  // I/O:
  static public final String NUM_LINES_OPT = "numLines";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String HIERO_FORMAT_OPT = "hieroFormat";
  static public final String HIERO_FLAT_FORMAT_OPT = "hieroFlatFormat";
  public static final String ONE_INDEXED_ALIGNMENT_OPT = "oneIndexedAlignment";
  public static final String REVERSED_ALIGNMENT_OPT = "reversedAlignment";
  public static final String SAVE_PREFIX_OPT = "savePrefix";

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
         HIERO_FORMAT_OPT, HIERO_FLAT_FORMAT_OPT,
         Rule.MAX_UNALIGNED_RHS_OPT, REVERSED_ALIGNMENT_OPT, 
         ONE_INDEXED_ALIGNMENT_OPT, SAVE_PREFIX_OPT));
     ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
     ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  final String fCorpus, eCorpus, aCorpus, eParsedCorpus;
  final AlignmentGraph ag = new AlignmentGraph();
  final RuleIndex ruleIndex = new RuleIndex();
  final List<FeatureExtractor> extractors =  new ArrayList<FeatureExtractor>();
  final int maxLHS, maxRHS;
  final boolean hieroFormat, hieroFlatFormat;
  final String savePrefix;
  int startAtLine, endAtLine;
  UnigramRuleFilter filter = null;

  public RuleExtractor(Properties prop) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    // Mandatory arguments:
    aCorpus = prop.getProperty(A_CORPUS_OPT);
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);
    eParsedCorpus = prop.getProperty(E_PARSED_CORPUS_OPT);
    // Optional arguments:
    String extractorStr = prop.getProperty(EXTRACTORS_OPT,
         "mt.syntax.train.RelativeFrequencyFeatureExtractor");
    String[] extractorNames = extractorStr.split(":");
    int maxCompositions = Integer.parseInt(prop.getProperty(MAX_COMPOSITIONS_OPT,"0"));
    AlignmentGraph.setMaxCompositions(maxCompositions);
    maxLHS = Integer.parseInt(prop.getProperty(MAX_LHS_SIZE_OPT,Integer.toString(Integer.MAX_VALUE)));
    maxRHS = Integer.parseInt(prop.getProperty(MAX_RHS_SIZE_OPT,Integer.toString(Integer.MAX_VALUE)));
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT,Integer.toString(0)));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT,Integer.toString(Integer.MAX_VALUE)));
    savePrefix = prop.getProperty(SAVE_PREFIX_OPT,"out");
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT,"-1"));
    if(numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    if(prop.getProperty(FILTER_CORPUS_OPT) != null)
      filter = new UnigramRuleFilter(prop.getProperty(FILTER_CORPUS_OPT));
    if(prop.getProperty(Rule.MAX_UNALIGNED_RHS_OPT) != null)
      Rule.MAX_UNALIGNED_RHS = Integer.parseInt(prop.getProperty(Rule.MAX_UNALIGNED_RHS_OPT));
    hieroFormat = Boolean.parseBoolean(prop.getProperty(HIERO_FORMAT_OPT,"false"));
    hieroFlatFormat = Boolean.parseBoolean(prop.getProperty(HIERO_FLAT_FORMAT_OPT,"false"));
    // Initialize extractors:
    for(String extractorName : extractorNames) {
      FeatureExtractor extractor = (FeatureExtractor) Class.forName(extractorName).newInstance();
      extractors.add(extractor);
      extractor.init(ruleIndex,prop);
    }
    ag.setFeatureExtractors(extractors);
  }

  public void extractRules() {
    LineNumberReader aReader = IOTools.getReaderFromFile(aCorpus);
    LineNumberReader fReader = IOTools.getReaderFromFile(fCorpus);
    LineNumberReader eReader =
      (eCorpus != null) ? IOTools.getReaderFromFile(eCorpus) : null;
    LineNumberReader eTreeReader = IOTools.getReaderFromFile(eParsedCorpus);

    long startStepTimeMillis = System.currentTimeMillis();
    boolean fatalError = false;
    for(int line=0; !fatalError && line <= endAtLine; ++line) {
      String aLine="", fLine="", eTreeLine, eLine=null;
      try {
        aLine = aReader.readLine();
        fLine = fReader.readLine();
        eTreeLine = eTreeReader.readLine();
        if(eReader != null)
          eLine = eReader.readLine();
        if(line < startAtLine)
          continue;
        boolean skip=false;
        boolean done=false;
        if(eTreeLine == null && fLine == null && aLine == null)
          done=true;
        if(line % 10000 == 0 || done) {
          long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
          long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
          double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
          startStepTimeMillis = System.currentTimeMillis();
          System.err.printf
           ("line %d (secs = %.3f, totalmem = %dm, freemem = %dm, %s)...\n",
            line, totalStepSecs, totalMemory, freeMemory, ruleIndex.getSizeInfo());
        }
        if(done)
          break;
        if("".equals(eTreeLine) || "".equals(fLine) || "".equals(aLine)) {
          skip=true;
          if(DEBUG)
            System.err.printf("RuleExtractor: extractRules: empty line at %d\n",line);
        }
        if(eTreeLine == null || fLine == null || aLine == null) {
          fatalError = true;
          throw new IOException("Wrong number of lines at: "+line);
        }
        if(skip)
          continue;
        ag.init(aLine, fLine, eTreeLine, eLine);
        for(Rule r : ag.extractRules()) {
          if(filter != null && !filter.keep(r))
            continue;
          if(r.lhsLabels.length > maxLHS || r.rhsLabels.length > maxRHS)
            continue;
          int ruleId = ruleIndex.getRuleId(r);
          int rootId = ruleIndex.getRootId(r);
          int lhsId = ruleIndex.getLHSId(r);
          int rhsId = ruleIndex.getRHSId(r);
          for(FeatureExtractor extractor : extractors)
            extractor.extractFeatures(r,ruleId,rootId,lhsId,rhsId);
        }
      } catch(Exception e) {
        System.err.printf("Exception at line: %d\n",line);
        System.err.printf("RuleExtractor: extractRules: line: %d\n",line);
        System.err.printf("RuleExtractor: extractRules: f: %s\n",fLine);
        System.err.printf("RuleExtractor: extractRules: e: %s\n",eLine);
        System.err.printf("RuleExtractor: extractRules: a: %s\n",aLine);
        e.printStackTrace();
      }
    }
    System.err.printf("Done with rule extraction. Printing %d rules:\n",ruleIndex.getCollection().size());
    int count=-1;
    for(Rule r : ruleIndex.getCollection()) {
      int ruleId = ruleIndex.getRuleId(r);
      int rootId = ruleIndex.getRootId(r);
      int lhsId = ruleIndex.getLHSId(r);
      int rhsId = ruleIndex.getRHSId(r);
      if(++count % 100000 == 0) {
        long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
        long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
        double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
        startStepTimeMillis = System.currentTimeMillis();
        System.err.printf
         ("%d rules printed (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
          count, totalStepSecs, totalMemory, freeMemory);
      }
      // Print rule:
      if(hieroFlatFormat) {
        //System.out.printf("[%s] ||| %s ||| %s |||",
        //   IString.getString(r.lhsStruct[0]));
      //} else if(hieroFormat) {
      } else {
        System.out.printf("%s |||",r.toString());
      }
      // Rule-level features:
      for(FeatureExtractor extractor : extractors)
        for(double s : extractor.score(r,ruleId,rootId,lhsId,rhsId)) {
          System.out.print(" ");
          System.out.print((float)s);
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
     " -extractors <class1>[:<class2>:...:<classN>]\n"+
     " -fFilterCorpus <file> : filter against a specific dev/test set\n"+
     " -numLines <n> : number of lines to process (<0 : all)\n"+
     " -startAtLine <n> : start at line <n> (<0 : all)\n"+
     " -endAtLine <n> : end at line <n> (<0 : all)\n"+
     " -maxLHS <n> : maximum size of LHS\n"+
     " -maxRHS <n> : maximum size of RHS\n"+
     " -maxUnalignedRHS <n> : maximum number of unaligned words in RHS\n"+
     " -hieroFormat: print rules in Hiero format (preserving STSG equivalence)\n"+
     " -hieroFlatFormat: print rules in Hiero format (not preserving STSG equivalent)\n"
    );
  }

  public static void main(String[] args)
      throws IllegalAccessException, InstantiationException, ClassNotFoundException {
    Properties prop = StringUtils.argsToProperties(args);
    String configFile = prop.getProperty(CONFIG_OPT);
    if(configFile != null) {
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
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }
    if (!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set<Object> extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      try {
        throw new RuntimeException(String.format
         ("The following fields are unrecognized: %s\n", extraFields));
      } catch(Exception e) {
        e.printStackTrace();
        usage();
        System.exit(1);
      }
    }
    AlignmentGraph.reversedAlignment 
     = Boolean.parseBoolean(prop.getProperty(REVERSED_ALIGNMENT_OPT,"true"));
    AlignmentGraph.oneIndexedAlignment 
     = Boolean.parseBoolean(prop.getProperty(ONE_INDEXED_ALIGNMENT_OPT,"false"));
    new RuleExtractor(prop).extractRules();
  }
}
