package mt;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author Daniel Cer
 */
public class ARPALanguageModel implements LanguageModel<IString> {

  private final String name;
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

  private static String readLineNonNull(LineNumberReader reader) throws IOException {
    String inline = reader.readLine();
    if (inline == null) {
      throw new RuntimeException(String.format("premature end of file"));
    }
    return inline;
  }

  FixedLengthIntegerArrayRawIndex[] tables;
  float[][] probs;
  float[][] bows;

  private static final int MAX_GRAM = 10; // highest order ngram possible
  private static final float LOAD_MULTIPLIER = (float)1.7;

  private static final WeakHashMap<String, ARPALanguageModel> lmStore = new WeakHashMap<String, ARPALanguageModel>();

  static ARPALanguageModel load(String filename) throws IOException {
    File f = new File(filename);
    String filepath = f.getAbsolutePath();
    if (lmStore.containsKey(filepath)) return lmStore.get(filepath);

    ARPALanguageModel alm = new ARPALanguageModel(filename);
    lmStore.put(filepath, alm);

    return alm;
  }

  private ARPALanguageModel(String filename) throws IOException {
    File f = new File(filename);
    name = String.format("APRA(%s)",f.getName());
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
  private double scoreR(Sequence<IString> sequence) {
    int[] ngramInts = Sequences.toIntArray(sequence);
    int index;

    index = tables[ngramInts.length-1].getIndex(ngramInts);
    if (index >= 0) { // found a match
      return probs[ngramInts.length-1][index];
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

    return p;
  }

  public double score(Sequence<IString> sequence) {
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder   = (tables.length < sequenceSz ? tables.length : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz-maxOrder, sequenceSz);
    }

    return scoreR(ngram);
  }

  public int order() {
    return tables.length;
  }

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > tables.length-1) return false;
    int[] prefixInts = Sequences.toIntArray(prefix);
    int index = tables[prefixInts.length-1].getIndex(prefixInts);
    if (index < 0) return false;
    double bow = bows[prefixInts.length-1][index];

    if (bow != bow) return false;

    return true;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence to score\"\n");
      System.exit(-1);
    }

    String model = args[0]; String sent = args[1];
    System.out.printf("Loading lm: %s...\n", model);
    ARPALanguageModel lm = new ARPALanguageModel(model);
    System.out.printf("done loading lm.\n");

    System.out.printf("Sentence: %s\n", sent);
    double score = LanguageModels.scoreSequence(lm, new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s"))));
    System.out.printf("Sequence score: %f score_log10: %f\n", score, score/Math.log(10));
  }


}
