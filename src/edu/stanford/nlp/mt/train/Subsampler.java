package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.SimpleSequence;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Subsample corpus, adding more training sentence pairs until each ngram of a
 * given dev/test set is seen at least N times in the training data (this is
 * close to the Chris Dyer implementation in /u/nlp/data/gale/scr/umd-mttools.jar).
 * Note that, due phrase extraction heuristics, some of these ngrams may not
 * correspond to any phrase (e.g., the N first occurences in the training data
 * of a particular ngram violate phrase extraction constraints, and occurrence N+1
 * does not).
 * 
 * @author Michel Galley
 */
public class Subsampler {

  private static final boolean DETAILED_DEBUG = false;

  static public final String INPUT_ROOT_OPT = "in";
  static public final String OUTPUT_ROOT_OPT = "out";
  static public final String F_EXT_OPT = "f";
  static public final String E_EXT_OPT = "e";
  static public final String A_EXT_OPT = "align";
  static public final String FILTER_CORPUS_OPT = "fFilterCorpus";
  
  static public final String CONFIG_OPT = "config";
  static public final String NUM_LINES_OPT = "numLines";
  static public final String TARGET_COUNT_OPT = "targetCount";
  static public final String START_AT_LINE_OPT = "startAtLine";
  static public final String END_AT_LINE_OPT = "endAtLine";
  static public final String MAX_FERTILITY_OPT = "maxFertility";
  static public final String LOWERCASE_OPT = "lowercase";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(
       INPUT_ROOT_OPT, OUTPUT_ROOT_OPT,
       F_EXT_OPT, E_EXT_OPT, A_EXT_OPT,
       FILTER_CORPUS_OPT
     ));
    OPTIONAL_OPTS.addAll(Arrays.asList(
       NUM_LINES_OPT, TARGET_COUNT_OPT,
       START_AT_LINE_OPT, END_AT_LINE_OPT,
       MAX_FERTILITY_OPT, LOWERCASE_OPT,
       AbstractPhraseExtractor.MAX_PHRASE_LEN_OPT,
       AbstractPhraseExtractor.MAX_PHRASE_LEN_F_OPT
     ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  private String fCorpus, eCorpus, aCorpus;
  private String ofCorpus, oeCorpus, oaCorpus;
  private int startAtLine, endAtLine = -1, targetCount;
  private boolean lowercase;
  private final Set<SymmetricalWordAlignment> sents = new ObjectOpenHashSet<SymmetricalWordAlignment>();
  private final Set<Sequence<IString>> phrases = new ObjectOpenHashSet<Sequence<IString>>();
  private final Counter<Sequence<IString>> phraseCounts = new OpenAddressCounter<Sequence<IString>>();

  public Subsampler(Properties prop) throws IOException {
    analyzeProperties(prop);
  }

  public void analyzeProperties(Properties prop) throws IOException {

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
      Set<Object> extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      System.err.printf
       ("The following fields are unrecognized: %s\n", extraFields);
      usage();
      System.exit(1);
    }

    // Analyze props:
    // Mandatory arguments:
    String inputRoot = prop.getProperty(INPUT_ROOT_OPT);
    String outputRoot = prop.getProperty(OUTPUT_ROOT_OPT);
    String f = prop.getProperty(F_EXT_OPT);
    String e = prop.getProperty(E_EXT_OPT);
    String a = prop.getProperty(A_EXT_OPT);
    fCorpus = inputRoot+"."+f;
    eCorpus = inputRoot+"."+e;
    aCorpus = inputRoot+"."+a;
    ofCorpus = outputRoot+"."+f;
    oeCorpus = outputRoot+"."+e;
    oaCorpus = outputRoot+"."+a;

    // Phrase filtering arguments:
    String fFilterCorpus = prop.getProperty(FILTER_CORPUS_OPT);
    SourceFilter sourceFilter = new SourceFilter();
    sourceFilter.addPhrasesFromCorpus
      (fFilterCorpus, AbstractPhraseExtractor.maxPhraseLenF, Integer.MAX_VALUE, false);
    for (int i=0; i<sourceFilter.getSourceTable().size(); ++i) {
      int[] el = sourceFilter.getSourceTable().get(i);
      phrases.add(new SimpleSequence<IString>(true, IStrings.toIStringArray(el)));
    }
    // Other optional arguments:
    startAtLine = Integer.parseInt(prop.getProperty(START_AT_LINE_OPT,"-1"));
    endAtLine = Integer.parseInt(prop.getProperty(END_AT_LINE_OPT,"-2"))+1;
    int numLines = Integer.parseInt(prop.getProperty(NUM_LINES_OPT,"-1"));
    if(numLines > 0) {
      startAtLine = 0;
      endAtLine = numLines;
    }
    lowercase = Boolean.parseBoolean(prop.getProperty(LOWERCASE_OPT,"false"));
    targetCount = Integer.parseInt(prop.getProperty(TARGET_COUNT_OPT,"10"));
  }

  public void filter() {

    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    try {
      // Read data and process data:
      LineNumberReader
        fReader = IOTools.getReaderFromFile(fCorpus);
      LineNumberReader eReader = IOTools.getReaderFromFile(eCorpus);
      LineNumberReader aReader = IOTools.getReaderFromFile(aCorpus);

      int lineNb=0;
      for (String fLine;; ++lineNb) {
        fLine = fReader.readLine();
        boolean done = (fLine == null || lineNb == endAtLine);

        if(lineNb % 10000 == 0 || done) {
          long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
          long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
          double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
          startStepTimeMillis = System.currentTimeMillis();
          System.err.printf("line %d (subsample = %d, secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                            lineNb, sents.size(), totalStepSecs, totalMemory, freeMemory);
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
        if(lowercase) {
          fLine = fLine.toLowerCase();
          eLine = eLine.toLowerCase();
        }
        SymmetricalWordAlignment sent = new SymmetricalWordAlignment(fLine,eLine,aLine,false,false);
        if(!sents.contains(sent) && isNeeded(sent))
          sents.add(sent);
      }

      if(eReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
        throw new IOException("Target-language corpus contains extra lines!");
      if(aReader.readLine() != null && startAtLine < 0 && endAtLine < 0)
        throw new IOException("Alignment file contains extra lines!");

      fReader.close();
      eReader.close();
      aReader.close();

      double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
      System.err.printf("Done after %.3f seconds.\n", totalTimeSecs);
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  void write() {

    PrintStream
        fWriter = IOTools.getWriterFromFile(ofCorpus),
        eWriter = IOTools.getWriterFromFile(oeCorpus),
        aWriter = IOTools.getWriterFromFile(oaCorpus);

    for(SymmetricalWordAlignment sent : sents) {
      fWriter.println(sent.f().toString());
      eWriter.println(sent.e().toString());
      aWriter.println(sent.toString());
    }
    
    fWriter.close();
    eWriter.close();
    aWriter.close();
  }

  boolean isNeeded(AbstractWordAlignment sent) {
    Sequence<IString> fSent = sent.f();
    /*
    // In Chris Dyer's code; not sure this is really needed:
    int flen = fSent.size();
    if (sent.e().size() == 0)
      return false;
    if (flen > 10 && targetFtoERatio != 0.0f) {
        double ratio = sent.ratioFtoE();
        if (flen > 10 &&
            (ratio > 1.3f * targetFtoERatio ||
             ratio * 1.3f < targetFtoERatio))
          continue;
      }
    */
       
    // Get all subsequences:
    for(int i=0; i<fSent.size(); ++i) {
      for(int j=i; j<fSent.size() && j-i<AbstractPhraseExtractor.maxPhraseLenF; ++j) {
        Sequence<IString> fPhrase = fSent.subsequence(i,j+1);
        if(phrases.contains(fPhrase) && sent.isAdmissiblePhraseF(i,j)) {
          double count = phraseCounts.incrementCount(fPhrase);
          if(count <= targetCount) {
            if(DETAILED_DEBUG) {
              System.err.printf("Sentence:\n%s\nneeded because of:\n%s\n",sent.toString(),fPhrase.toString());
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  static void usage() {
    System.err.print
    ("Usage: java edu.stanford.nlp.mt.train.Subsampler [ARGS]\n"+
     "Mandatory arguments:\n"+
     " -in <root> : root name of input files\n"+
     " -out <root> : root name of output files\n"+
     " -f <id> : source-language extension/identifier\n"+
     " -e <id> : target-language extension/identifier\n"+
     " -align <file> : alignment extension/identifier\n"+
     " -fFilterCorpus <file> : filter against a specific dev/test set\n"+
     "Optional arguments:\n"+
     " -targetCount <n> : target n-gram count (default: 10)\n"+
     " -maxLen <n> : max phrase length\n"+
     " -numLines <n> : number of lines to process (<0 : all)\n"+
     " -startAtLine <n> : start at line <n> (<0 : all)\n"+
     " -endAtLine <n> : end at line <n> (<0 : all)\n");
  }

  public static void main(String[] args) {
    
    Properties prop = StringUtils.argsToProperties(args);
    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");

    System.err.println("subsampling started at: "+formatter.format(new Date()));

    try {
      Subsampler e = new Subsampler(prop);
      e.filter();
      e.write();
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("subsampling ended at: "+formatter.format(new Date()));
  }

}
