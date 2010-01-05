package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.IOException;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;

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

  static public final String TEST_OPT = "test";
  static public final String TRAIN_OPT = "train";

  static public final String MIN_LEN_OPT = "min";
  static public final String MAX_LEN_OPT = "max";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(Arrays.asList(
       TRAIN_OPT, TEST_OPT
     ));
    OPTIONAL_OPTS.addAll(Arrays.asList(
       MIN_LEN_OPT, MAX_LEN_OPT
     ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  private static final int minLen = 15, maxLen = 50, windowSize = 20;
  private final Counter<Sequence<IString>>[] counters;
  private final  Map<String,String> ngramSources;

  @SuppressWarnings("unchecked")
  public CorpusOverlapFinder(Properties prop) throws IOException {
    analyzeProperties(prop);
    counters = new OpenAddressCounter[maxLen+1];
    ngramSources = new HashMap<String,String>();
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
      Set<Object> extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      System.err.printf
       ("The following fields are unrecognized: %s\n", extraFields);
      usage();
      System.exit(1);
    }
  }

  
  public void findDuplicates(String file) throws IOException {

    // Init test-set n-gram counts:
    for(Counter<Sequence<IString>> c : counters)
      for(Sequence<IString> s : c.keySet())
        c.setCount(s,0);

    // Count test-set n-grams appearing in rootName:
    extractPhrases(file, true);
  }

  // Returns percentage of n-gram matches:
  private void extractPhrases(String file, boolean train) {

    List<Set<String>> matches = new ArrayList<Set<String>>();
    List<String> eLines = new ArrayList<String>();
    for(String eLine : ObjectBank.getLineIterator(file)) {
      Set<String> localMatches = new HashSet<String>();
      String nopunc = eLine.replaceAll("[^\\w\\d ]"," ");
      Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(nopunc.toLowerCase().split("\\s+")));
      for(int i=0; i<f.size(); ++i) {
        for(int j=i+minLen-1; j<f.size() && j-i<maxLen; ++j) {
          Sequence<IString> phrase = f.subsequence(i,j+1);
          int sz = phrase.size();
          Counter<Sequence<IString>> c = counters[sz];
          if(train) {
            // Training data:
            if(c.containsKey(phrase)) {
              c.incrementCount(phrase);
              localMatches.add(phrase.toString());
            }
          } else {
            // Test data:
            c.setCount(phrase,0);
            ngramSources.put(phrase.toString(),eLine);
          }
        }
      }
      if(train) {
        eLines.add(eLine);
        matches.add(localMatches);
        if(!localMatches.isEmpty()) {
          System.err.printf("train: %s\n",eLine);
          for(String ngram : localMatches) {
            System.err.printf("    match: {{{ %s }}}\n", ngram);
            System.err.printf("   source: {{{ %s }}}\n",ngramSources.get(ngram));
          }
        }
      }
    }

    for(int i=0; i<matches.size(); ++i) {
      int c = 0, total = 0;
      int r1=Math.max(i-windowSize,0);
      int r2=Math.min(i+windowSize,matches.size()-1);
      for(int j=r1; j<=r2; ++j) {
        ++total;
        if(!matches.get(j).isEmpty())
          ++c;
      }
      double o = c*1.0/total;
      System.out.printf("%d\t%f\t%s\n",i+1,o,eLines.get(i));
      for(String m : matches.get(i)) {
        System.out.printf("    match: {{{ %s }}}\n",m);
        System.out.printf("   source: {{{ %s }}}\n",ngramSources.get(m));
      }
    }
  }

  static void usage() {
    System.err.print
    ("Usage: java edu.stanford.nlp.mt.train.CorpusOverlapFinder test train1 ... trainN\n");
  }

  public static void main(String[] args) {
    
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    Properties prop = StringUtils.argsToProperties(args);

    System.err.println("CorpusOverlapFinder started at: "+formatter.format(new Date()));

    try {
      CorpusOverlapFinder t = new CorpusOverlapFinder(prop);
      String[] train = prop.getProperty(TRAIN_OPT).split("\\s+");
      for(String aTrain : train) {
        System.err.println("Finding matches in: " + aTrain);
        t.findDuplicates(aTrain);
      }
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("CorpusOverlapFinder ended at: "+formatter.format(new Date()));
  }

}
