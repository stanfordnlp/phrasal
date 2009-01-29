package mt.train;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.io.File;
import java.io.PrintStream;

import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.base.IOTools;

/**
 * Class for finding long n-gram sequences that appear in both training and test data.
 * If these sequences are really long, the overlap may not have occurred by chance.
 * This class is particularly useful if you need to move a given corpus (e.g., CTB)
 * from training to test data. Since MT training data often contains many duplicate
 * sources, it is sometimes difficult to identify such overlaps.
 * The method main prints information about overlaps to stderr and training data
 * that doesn't seem to overlap test data (ngram matches of length < 10) to files
 * specified as arguments (-out).
 *
 * @author Michel Galley
 */
public class CorpusOverlapFinder {

  static public final String F_EXT_OPT = "f";
  static public final String E_EXT_OPT = "e";
  static public final String A_EXT_OPT = "align";
  static public final String TEST_OPT = "test";
  static public final String TRAIN_OPT = "train";

  static public final String MIN_LEN_OPT = "min";
  static public final String MAX_LEN_OPT = "max";
  static public final String MAX_DISPLAY_OPT = "display";
  static public final String OUTPUT_ROOT_OPT = "out";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  private String f, e, a;
  private PrintStream fWriter, eWriter, aWriter;

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(
       F_EXT_OPT, E_EXT_OPT, A_EXT_OPT, TRAIN_OPT, TEST_OPT
     ));
    OPTIONAL_OPTS.addAll(Arrays.asList(
       MIN_LEN_OPT, MAX_LEN_OPT, MAX_DISPLAY_OPT,
       OUTPUT_ROOT_OPT
     ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  private static final int minLen = 10, maxLen = 50, maxDisplay = 10;
  private Counter<Sequence<IString>>[] counters;

  @SuppressWarnings("unchecked")
  public CorpusOverlapFinder(Properties prop) throws IOException {
    analyzeProperties(prop);
    counters = new OpenAddressCounter[maxLen+1];
    for(int i=0; i<=maxLen; ++i)
      counters[i] = new OpenAddressCounter<Sequence<IString>>();
    String test = prop.getProperty(TEST_OPT);
    System.err.println("Extracting ngrams from test corpus: " + test);
    extractPhrases(test, false);
  }

  public void analyzeProperties(Properties prop) throws IOException {

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
      Set extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      System.err.printf
       ("The following fields are unrecognized: %s\n", extraFields);
      usage();
      System.exit(1);
    }

    // Analyze props:
    // Mandatory arguments:
    String outputRoot = prop.getProperty(OUTPUT_ROOT_OPT);
    f = prop.getProperty(F_EXT_OPT);
    e = prop.getProperty(E_EXT_OPT);
    a = prop.getProperty(A_EXT_OPT);
    String ofCorpus = outputRoot+"."+f;
    String oeCorpus = outputRoot+"."+e;
    String oaCorpus = outputRoot+"."+a;
    fWriter = IOTools.getWriterFromFile(ofCorpus);
    eWriter = IOTools.getWriterFromFile(oeCorpus);
    aWriter = IOTools.getWriterFromFile(oaCorpus);
  }

  
  public void findDuplicates(String rootName) throws IOException {

    for(Counter<Sequence<IString>> c : counters)
      for(Sequence<IString> s : c.keySet())
        c.setCount(s,0);

    extractPhrases(rootName, true);

    int szCount = 0;
    for(int i=maxLen; i>=minLen; --i) {
      Counter<Sequence<IString>> c = counters[i];
      double count = 0.0;
      for(Map.Entry<Sequence<IString>,Double> e : c.entrySet()) {
        double sc = e.getValue();
        if(sc > 0.0)
          System.err.println("match: "+e.getKey().toString());
        count += sc;
      }
      if(count > 0.0) {
        System.err.printf("len=%d count=%f\n", i, count);
        if(++szCount == maxDisplay)
          break;
      }
    }
  }


  private void extractPhrases(String rootName, boolean train) {

    String eFile = rootName+"."+e;
    String fFile = rootName+"."+f;
    String aFile = rootName+"."+a;

    Iterator<String> ait = null, fit = null;
    if(train) {
      if(new File(fFile).exists())
        fit = ObjectBank.getLineIteratorObjectBank(fFile).iterator();
      if(new File(aFile).exists())
        ait = ObjectBank.getLineIteratorObjectBank(aFile).iterator();
    }

    for(String eLine : ObjectBank.getLineIteratorObjectBank(eFile)) {
      String fLine = (fit != null) ? fit.next() : "";
      String aLine = (ait != null) ? ait.next() : "";
      boolean significantOverlap = false;
      Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(eLine.toLowerCase().split("\\s+")));
      for(int i=0; i<f.size(); ++i) {
        for(int j=i; j<f.size() && j-i<maxLen; ++j) {
          Sequence<IString> phrase = f.subsequence(i,j+1);
          int sz = phrase.size();
          Counter<Sequence<IString>> c = counters[sz];
          if(train) {
            // Training data:
            if(c.containsKey(phrase)) {
              c.incrementCount(phrase);
              if(sz >= minLen)
                significantOverlap = true;
            }
          } else {
            // Test data:
            c.setCount(phrase,0);
          }
        }
      }
      if(train && !significantOverlap) {
        eWriter.println(eLine);
        fWriter.println(fLine);
        aWriter.println(aLine);
      } else if(train) {
        System.err.println("Skipping: "+eLine);
      }
    }
  }

  static void usage() {
    System.err.print
    ("Usage: java mt.train.CorpusOverlapFinder test train1 ... trainN\n");
  }

  public static void main(String[] args) {
    
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    Properties prop = StringUtils.argsToProperties(args);

    System.err.println("CorpusOverlapFinder started at: "+formatter.format(new Date()));

    try {
      CorpusOverlapFinder t = new CorpusOverlapFinder(prop);
      String[] train = prop.getProperty(TRAIN_OPT).split("\\s+");
      for(int i=0; i<train.length; ++i) {
        System.err.println("Finding matches in: " + train[i]);
        t.findDuplicates(train[i]);
      }
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("CorpusOverlapFinder ended at: "+formatter.format(new Date()));
  }

}
