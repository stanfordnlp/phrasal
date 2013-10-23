package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

/**
 * 
 * @author Daniel Cer
 */
public class ARPALanguageModel implements LanguageModel<IString> {

  // public static final String USE_SRILM_PROPERTY = "SRILM";
  // public static final boolean USE_SRILM =
  // Boolean.parseBoolean(System.getProperty(USE_SRILM_PROPERTY, "false"));

  static boolean verbose = false;

  protected final String name;
  
  @Override
  public String getName() {
    return name;
  }

  @Override
  public IString getStartToken() {
    return TokenUtils.START_TOKEN;
  }

  @Override
  public IString getEndToken() {
    return TokenUtils.END_TOKEN;
  }

  protected static String readLineNonNull(LineNumberReader reader)
      throws IOException {
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
  protected static final float LOAD_MULTIPLIER = (float) 1.7;

  protected static final WeakHashMap<String, ARPALanguageModel> lmStore = new WeakHashMap<String, ARPALanguageModel>();

  public static LanguageModel<IString> load(String filename) throws IOException {
    return LanguageModels.load(filename, null);
  }

  public ARPALanguageModel(String filename) throws IOException {
    name = String.format("APRA(%s)", filename);
    init(filename);
  }

  protected void init(String filename) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    
    // skip everything until the line that begins with '\data\'
    while (!readLineNonNull(reader).startsWith("\\data\\")) {
    }

    // read in ngram counts
    int[] ngramCounts = new int[MAX_GRAM];
    String inline;
    int maxOrder = 0;
    while ((inline = readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("=");
      int ngramOrder = Integer.parseInt(fields[0]);
      if (ngramOrder > MAX_GRAM) {
        throw new RuntimeException(String.format("Max n-gram order: %d\n",
            MAX_GRAM));
      }
      ngramCounts[ngramOrder - 1] = Integer.parseInt(fields[1].replaceAll(
          "[^0-9]", ""));
      if (maxOrder < ngramOrder)
        maxOrder = ngramOrder;
    }

    tables = new FixedLengthIntegerArrayRawIndex[maxOrder];
    probs = new float[maxOrder][];
    bows = new float[maxOrder - 1][];
    for (int i = 0; i < maxOrder; i++) {
      int tableSz = Integer
          .highestOneBit((int) (ngramCounts[i] * LOAD_MULTIPLIER)) << 1;
      tables[i] = new FixedLengthIntegerArrayRawIndex(i + 1,
          Integer.numberOfTrailingZeros(tableSz));
      probs[i] = new float[tableSz];
      if (i + 1 < maxOrder)
        bows[i] = new float[tableSz];
    }

    float log10LogConstant = (float) Math.log(10);

    // read in the n-gram tables one by one
    for (int order = 0; order < maxOrder; order++) {
      System.err.printf("Reading %d %d-grams...\n", probs[order].length,
          order + 1);
      String nextOrderHeader = String.format("\\%d-grams:", order + 1);
      IString[] ngram = new IString[order + 1];
      int[] ngramInts = new int[order + 1];

      // skip all material upto the next n-gram table header
      while (!readLineNonNull(reader).startsWith(nextOrderHeader)) {
      }

      // read in table
      while (!(inline = readLineNonNull(reader)).equals("") && !(inline.equals("\\end\\"))) {
        // during profiling, 'split' turned out to be a bottle neck
        // and using StringTokenizer is about twice as fast
        StringTokenizer tok = new StringTokenizer(inline);
        float prob = strToFloat(tok.nextToken()) * log10LogConstant;

        for (int i = 0; i <= order; i++) {
          ngram[i] = new IString(tok.nextToken());
          ngramInts[i] = ngram[i].getId();
        }

        float bow = (tok.hasMoreElements() ? Float.parseFloat(tok.nextToken())
            * log10LogConstant : Float.NaN);
        int index = tables[order].insertIntoIndex(ngramInts);
        probs[order][index] = prob;
        if (order < bows.length)
          bows[order][index] = bow;
      }
    }

    // print some status information
    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err
        .printf(
            "Done loading arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n",
            filename, maxOrder, (postLMLoadMemUsed - preLMLoadMemUsed)
                / (1024 * 1024), loadTimeMillis / 1000.0);
    reader.close();
  }

  private static float strToFloat(String token) {
    // Escape for KenLM
    return token.equals("-inf") ? Float.NEGATIVE_INFINITY : Float.parseFloat(token);
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
   * p(wd3|wd1,wd2)= if(trigram exists) p_3(wd1,wd2,wd3) else if(bigram w1,w2
   * exists) bo_wt_2(w1,w2)*p(wd3|wd2) else p(wd3|w2)
   * 
   * p(wd2|wd1)= if(bigram exists) p_2(wd1,wd2) else bo_wt_1(wd1)*p_1(wd2)
   * 
   */
  protected double scoreR(Sequence<IString> sequence) {
    int[] ngramInts = Sequences.toIntArray(sequence);
    int index;

    index = tables[ngramInts.length - 1].getIndex(ngramInts);
    if (index >= 0) { // found a match
      double p = probs[ngramInts.length - 1][index];
      if (verbose)
        System.err.printf("EM: scoreR: seq: %s logp: %f\n", sequence.toString(), p);
      return p;
    }
    if (ngramInts.length == 1) {
      return Double.NEGATIVE_INFINITY; // OOV
    }
    Sequence<IString> prefix = sequence.subsequence(0, ngramInts.length - 1);
    int[] prefixInts = Sequences.toIntArray(prefix);
    index = tables[prefixInts.length - 1].getIndex(prefixInts);
    double bow = 0;
    if (index >= 0)
      bow = bows[prefixInts.length - 1][index];
    if (Double.isNaN(bow))
      bow = 0.0; // treat NaNs as bow that are not found at all
    double p = bow + scoreR(sequence.subsequence(1, ngramInts.length));
    if (verbose)
      System.err.printf("scoreR: seq: %s logp: %f [%f] bow: %f\n",
          sequence.toString(), p, p / Math.log(10), bow);
    return p;
  }

  /**
   * Determines whether we are computing p( <s> | <s> ... ) or p( w_n=</s> |
   * w_n-1=</s> ..), in which case log-probability is zero. This function is
   * only useful if the translation hypothesis contains explicit <s> and </s>,
   * and always returns false otherwise.
   */
  static boolean isBoundaryWord(Sequence<IString> sequence) {
    if (sequence.size() == 2 && sequence.get(0).equals(TokenUtils.START_TOKEN)
        && sequence.get(1).equals(TokenUtils.START_TOKEN)) {
      return true;
    }
    if (sequence.size() > 1) {
      int last = sequence.size() - 1;
      IString endTok = TokenUtils.END_TOKEN;
      if (sequence.get(last).equals(endTok)
          && sequence.get(last - 1).equals(endTok)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public double score(Sequence<IString> sequence) {
    if (isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder = (probs.length < sequenceSz ? probs.length : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
    }

    double score = scoreR(ngram);
    if (verbose)
      System.err.printf("score: seq: %s logp: %f [%f]\n", sequence.toString(),
          score, score / Math.log(10));
    return score;
  }

  @Override
  public int order() {
    return probs.length;
  }

  @Override
  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > probs.length - 1)
      return false;
    int[] prefixInts = Sequences.toIntArray(prefix);
    int index = tables[prefixInts.length - 1].getIndex(prefixInts);
    if (index < 0)
      return false;
    double bow = bows[prefixInts.length - 1][index];
    return !Double.isNaN(bow);
  }
}
