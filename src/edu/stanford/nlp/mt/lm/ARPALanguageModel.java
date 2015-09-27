package edu.stanford.nlp.mt.lm;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.StringTokenizer;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IntegerArrayRawIndex;
import edu.stanford.nlp.mt.util.ProbingIntegerArrayRawIndex;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * A pure Java implementation of an n-gram language model loaded from
 * an ARPA-format file.
 * 
 * @author Daniel Cer
 */
public class ARPALanguageModel implements LanguageModel<IString> {

  static boolean verbose = false;

  // in srilm -99 is -infinity
  public static final double UNKNOWN_WORD_SCORE = -100.0;

  protected final String name;
  
  private static final ARPALMState EMPTY_STATE = new ARPALMState(0.0, Sequences.emptySequence());
  private static final int[] UNK_QUERY = new int[]{TokenUtils.UNK_TOKEN.id};
  
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

    tables = new ProbingIntegerArrayRawIndex[maxOrder];
    probs = new float[maxOrder][];
    bows = new float[maxOrder - 1][];
    for (int i = 0; i < maxOrder; i++) {
      int tableSz = Integer
          .highestOneBit((int) (ngramCounts[i] * LOAD_MULTIPLIER)) << 1;
      tables[i] = new ProbingIntegerArrayRawIndex();
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
  protected ARPALMState scoreNgram(Sequence<IString> sequence) {
    int[] ngramInts = Sequences.toIntArray(sequence);
    int index;

    index = tables[ngramInts.length - 1].getIndex(ngramInts);
    if (index >= 0) { // found a match
      double p = probs[ngramInts.length - 1][index];
      if (verbose)
        System.err.printf("EM: scoreR: seq: %s logp: %f%n", sequence.toString(), p);
      return new ARPALMState(p, sequence.subsequence(1, sequence.size()));
    }
    
    // OOV
    if (ngramInts.length == 1) {
      // First check for an <unk> class, which is present for KenLM
      // but not necessarily for SRILM.
      index = tables[0].getIndex(UNK_QUERY);
      double p = index >= 0 ? probs[0][index] : UNKNOWN_WORD_SCORE;
      return new ARPALMState(p, Sequences.emptySequence());
    }
    
    // Backoff recursively
    Sequence<IString> prefix = sequence.subsequence(0, ngramInts.length - 1);
    int[] prefixInts = Sequences.toIntArray(prefix);
    index = tables[prefixInts.length - 1].getIndex(prefixInts);
    double bow = 0;
    if (index >= 0) {
      bow = bows[prefixInts.length - 1][index];
    }
    if (Double.isNaN(bow)) {
      bow = 0.0; // treat NaNs as bow that are not found at all
    }
    ARPALMState state = scoreNgram(sequence.subsequence(1, ngramInts.length));
    double p = bow + state.getScore();
    if (verbose) {
      System.err.printf("scoreR: seq: %s logp: %f [%f] bow: %f\n",
          sequence.toString(), p, p / Math.log(10), bow);
    }
    return new ARPALMState(p, state);
  }

  @Override
  public LMState score(Sequence<IString> sequence, int startOffsetIndex, LMState priorState) {
    if (sequence.size() == 0) {
      // Source deletion rule
      return priorState == null ? EMPTY_STATE : priorState;
    } else if (sequence.size() == 1 && priorState == null && sequence.get(0).equals(TokenUtils.START_TOKEN)) {
      // Special case: Source deletion rule (e.g., from the OOV model) at the start of a string
      return new ARPALMState(0.0f, sequence);
    }
    
    // Concatenate the state onto the sequence.
    if (priorState != null && priorState instanceof ARPALMState) {
      int seqLength = sequence.size();
    	sequence = ((ARPALMState) priorState).getState().concat(sequence);
    	startOffsetIndex += (sequence.size() - seqLength);
    }

    // Score the sequence
    double lmSumScore = 0.0;
    ARPALMState state = EMPTY_STATE;
    for (int pos = startOffsetIndex, limit = sequence.size(); pos < limit; pos++) {
      final int seqStart = Math.max(0, pos - order() + 1);
      Sequence<IString> ngram = sequence.subsequence(seqStart, pos + 1);
      state = scoreNgram(ngram);
      lmSumScore += state.getScore();
    }
    
    if (verbose) {
      System.err.printf("ARPALM: seq: %s  state: %s  score: %f%n", sequence.toString(),
          state.toString(), lmSumScore);
    }
    return new ARPALMState(lmSumScore, state);
  }

  @Override
  public int order() {
    return probs.length;
  }
}
