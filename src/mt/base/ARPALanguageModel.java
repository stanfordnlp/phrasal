package mt.base;

import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;


/**
 *
 * @author Daniel Cer
 */
public class ARPALanguageModel implements LanguageModel<IString> {

  public static final String QUANTIZED_LM_PROPERTY = "quantizedLM";
  public static final boolean QUANTIZED_LM = Boolean.parseBoolean(System.getProperty(QUANTIZED_LM_PROPERTY, "false"));

  public static final String USE_TRIE_PROPERTY = "trieLM";
  public static final boolean USE_TRIE = Boolean.parseBoolean(System.getProperty(USE_TRIE_PROPERTY, "false"));

  public static final String USE_SRILM_PROPERTY = "SRILM";
  public static final boolean USE_SRILM = Boolean.parseBoolean(System.getProperty(USE_SRILM_PROPERTY, "false"));

  static boolean verbose = false;

  protected final String name;
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");

  public String getName() {
    return name;
  }

  public IString getStartToken() {
    return START_TOKEN;
  }

  public IString getEndToken() {
    return END_TOKEN;
  }

  protected static String readLineNonNull(LineNumberReader reader) throws IOException {
    String inline = reader.readLine();
    if (inline == null) {
      throw new RuntimeException(String.format("premature end of file"));
    }
    return inline;
  }

  protected IntegerArrayRawIndex[] tables;
  private float[][] probs;
  private float[][] bows;

  protected static final int MAX_GRAM = 10; // highest order ngram possible
  protected static final float LOAD_MULTIPLIER = (float)1.7;

  protected static final WeakHashMap<String, ARPALanguageModel> lmStore = new WeakHashMap<String, ARPALanguageModel>();

  public static LanguageModel<IString> load(String filename) throws IOException {
    File f = new File(filename);
    String filepath = f.getAbsolutePath();
    if (lmStore.containsKey(filepath)) return lmStore.get(filepath);

    LanguageModel<IString> alm = QUANTIZED_LM ? new QuantizedARPALanguageModel(filename) :
        (USE_TRIE ? new TrieARPALanguageModel(filename) : 
        (USE_SRILM ? new SRILanguageModel(filename) : new ARPALanguageModel(filename)));
    if(alm instanceof ARPALanguageModel)
      lmStore.put(filepath, (ARPALanguageModel)alm);

    return alm;
  }

  protected ARPALanguageModel(String filename) throws IOException {
    name = String.format("APRA(%s)",filename);
    init(filename);
  }

  protected void init(String filename) throws IOException {
    File f = new File(filename);

    System.gc();
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();


    LineNumberReader reader = (filename.endsWith(".gz") ? new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)))) :
            new LineNumberReader(new FileReader(f)));

    // skip everything until the line that begins with '\data\'
    while (!readLineNonNull(reader).startsWith("\\data\\"));

    // read in ngram counts
    int[] ngramCounts = new int[MAX_GRAM];
    String inline;
    int maxOrder = 0;
    while ((inline = readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("=");
      int ngramOrder = Integer.parseInt(fields[0]);
      if (ngramOrder > MAX_GRAM) {
        throw new RuntimeException(String.format("Max n-gram order: %d\n", MAX_GRAM));
      }
      ngramCounts[ngramOrder-1] = Integer.parseInt(fields[1].replaceFirst("[^0-9].*$", ""));
      if (maxOrder < ngramOrder) maxOrder = ngramOrder;
    }

    tables = new FixedLengthIntegerArrayRawIndex[maxOrder];
    probs = new float[maxOrder][];
    bows = new float[maxOrder-1][];
    for (int i = 0; i < maxOrder; i++) {
      int tableSz = Integer.highestOneBit((int)(ngramCounts[i]*LOAD_MULTIPLIER))<<1;
      tables[i] = new FixedLengthIntegerArrayRawIndex(i+1, Integer.numberOfTrailingZeros(tableSz));
      probs[i] = new float[tableSz];
      if (i+1 < maxOrder) bows[i]  = new float[tableSz];
    }

    float log10LogConstant = (float)Math.log(10);

    // read in the n-gram tables one by one
    for (int order = 0; order < maxOrder; order++) {
      System.err.printf("Reading %d %d-grams...\n", probs[order].length, order+1);
      String nextOrderHeader = String.format("\\%d-grams:", order+1);
      IString[] ngram = new IString[order+1];
      int[] ngramInts = new int[order+1];

      // skip all material upto the next n-gram table header
      while (!readLineNonNull(reader).startsWith(nextOrderHeader));

      // read in table
      while (!(inline = readLineNonNull(reader)).equals("")) {
        // during profiling, 'split' turned out to be a bottle neck
        // and using StringTokenizer is about twice as fast
        StringTokenizer tok = new StringTokenizer(inline);
        float prob = Float.parseFloat(tok.nextToken()) * log10LogConstant;

        for (int i = 0; i <= order; i++) {
          ngram[i] = new IString(tok.nextToken());
          ngramInts[i] = ngram[i].getId();
        }

        float bow = (tok.hasMoreElements() ?
                Float.parseFloat(tok.nextToken()) * log10LogConstant : Float.NaN);
        int index = tables[order].insertIntoIndex(ngramInts);
        probs[order][index] = prob;
        if (order < bows.length) bows[order][index] = bow;
      }
    }

    System.gc();

    // print some status information
    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err.printf("Done loading arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n", filename, maxOrder,
            (postLMLoadMemUsed - preLMLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);
    reader.close();
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   *
   * From CMU language model headers:
   * ------------------------------------------------------------------
   *
   * This file is in the ARPA-standard format introduced by Doug Paul.
   *
   * p(wd3|wd1,wd2)= if(trigram exists)           p_3(wd1,wd2,wd3)
   *                 else if(bigram w1,w2 exists) bo_wt_2(w1,w2)*p(wd3|wd2)
   *                 else                         p(wd3|w2)
   *
   * p(wd2|wd1)= if(bigram exists) p_2(wd1,wd2)
   *                 else              bo_wt_1(wd1)*p_1(wd2)
   *
   */
  protected double scoreR(Sequence<IString> sequence) {
    int[] ngramInts = Sequences.toIntArray(sequence);
    int index;

    index = tables[ngramInts.length-1].getIndex(ngramInts);
    if (index >= 0) { // found a match
      double p = probs[ngramInts.length-1][index];
      if(verbose)
        System.err.printf("scoreR: seq: %s logp: %f\n", sequence.toString(), p);
      return p;
    }
    if (ngramInts.length == 1) {
      return Double.NEGATIVE_INFINITY; // OOV
    }
    Sequence<IString> prefix = sequence.subsequence(0, ngramInts.length-1);
    int[] prefixInts = Sequences.toIntArray(prefix);
    index = tables[prefixInts.length-1].getIndex(prefixInts);
    double bow = 0;
    if (index >= 0) bow = bows[prefixInts.length-1][index];
    if (bow != bow) bow = 0.0; // treat NaNs as bow that are not found at all
    double p = bow + scoreR(sequence.subsequence(1, ngramInts.length));
    if(verbose)
      System.err.printf("scoreR: seq: %s logp: %f bow: %f\n", sequence.toString(), p, bow);
    return p;
  }

  /**
   * Determines whether we are computing p( <s> | <s> ... ) or p( w_n=</s> | w_n-1=</s> ..),
   * in which case log-probability is zero. This function is only useful if the translation
   * hypothesis contains explicit <s> and </s>, and always returns false otherwise.
   */
  boolean isBoundaryWord(Sequence<IString> sequence) {
    if(sequence.size() == 2 && sequence.get(0).equals(getStartToken()) && sequence.get(1).equals(getStartToken())) {
      return true;
    }
    if(sequence.size() > 1) {
      int last = sequence.size()-1;
      IString endTok = getEndToken();
      if(sequence.get(last).equals(endTok) && sequence.get(last-1).equals(endTok)) {
        return true;
      }
    }
    return false;
  }

  public double score(Sequence<IString> sequence) {
    if(isBoundaryWord(sequence)) return 0.0;
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder   = (probs.length < sequenceSz ? probs.length : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz-maxOrder, sequenceSz);
    }

    double score = scoreR(ngram);
    if(verbose)
      System.err.printf("score: seq: %s logp: %f\n", sequence.toString(), score);
    return score;
  }

  public int order() {
    return probs.length;
  }

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > probs.length-1) return false;
    int[] prefixInts = Sequences.toIntArray(prefix);
    int index = tables[prefixInts.length-1].getIndex(prefixInts);
    if (index < 0) return false;
    double bow = bows[prefixInts.length-1][index];

    if (bow != bow) return false;
    return true;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence or file to score\"\n");
      System.exit(-1);
    }

    //verbose = true;
    String model = args[0]; String file = args[1];
    System.out.printf("Loading lm: %s...\n", model);
    LanguageModel<IString> lm = ARPALanguageModel.load(model);
    System.out.printf("done loading lm.\n");

    long startTimeMillis = System.currentTimeMillis();
    for(String sent : ObjectBank.getLineIterator(file)) {
      sent = sent.toLowerCase();
      System.out.printf("Sentence: %s\n", sent);
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score = LanguageModels.scoreSequence(lm, seq);
      System.out.printf("Sequence score: %f score_log10: %f\n", score, score/Math.log(10));
    }
    double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("secs = %.3f\n", totalSecs);
  }
}
