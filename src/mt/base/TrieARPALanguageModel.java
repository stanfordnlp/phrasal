package mt.base;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;


/**
 * Language model internally repesented as a trie. Takes 2-3 times
 * longer to load compared to ARPALanguageModel, but saves 30-50% memory.
 *
 * @author Michel Galley (trie representation)
 * @author Daniel Cer (code copied from ARPALanguageModel)
 */
public class TrieARPALanguageModel extends ARPALanguageModel {

  // Performance comparison with ARPALanguageModel:

  // ARPA header:
  // ngram 1=20311
  // ngram 2=11123350
  // ngram 3=30111902
  // ngram 4=34683198
  // ngram 5=42025894

  // ARPALanguageModel:
  //   mem used: 6436 MiB
  //   loading time: 481.885 s
  //   running time: 2707 s
  // TrieARPALanguageModel:
  //   mem used: 3514 MiB
  //   loading time: 1035.696 s
  //   running time: 2904 s
  //
  // If loading is deemed too slow, either switch back to ARPALanguageModel
  // or increase TrieIntegerArrayIndex.GROWTH_FACTOR.

  // TODO: enable quantization in this class

  protected TrieIntegerArrayIndex table;
  float[] mprobs;
  float[] mbows;
  int lmOrder;

  protected TrieARPALanguageModel(String filename) throws IOException {
    super(filename);
    System.err.printf("Using TrieARPALAnguageModel (order %d)\n",this.lmOrder);
  }

  protected void init(String filename) throws IOException {
    System.err.println("Init: "+this.lmOrder);
    File f = new File(filename);

    Runtime rt = Runtime.getRuntime();
    System.gc();
    long preLMLoadMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();


    LineNumberReader reader = (filename.endsWith(".gz") ? new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)))) :
            new LineNumberReader(new FileReader(f)));

    // skip everything until the line that begins with '\data\'
    while (!readLineNonNull(reader).startsWith("\\data\\"));

    // read in ngram counts
    int[] ngramCounts = new int[MAX_GRAM];
    String inline;
    lmOrder = 0;
    while ((inline = readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("=");
      int ngramOrder = Integer.parseInt(fields[0]);
      if (ngramOrder > MAX_GRAM) {
        throw new RuntimeException(String.format("Max n-gram order: %d\n", MAX_GRAM));
      }
      ngramCounts[ngramOrder-1] = Integer.parseInt(fields[1].replaceFirst("[^0-9].*$", ""));
      if (lmOrder < ngramOrder) lmOrder = ngramOrder;
    }

    int probTableSz = 0;
    int bowTableSz = 0;
    for (int i = 0; i < lmOrder; i++) {
      int tableSz = Integer.highestOneBit((int)(ngramCounts[i]*LOAD_MULTIPLIER))<<1;
      probTableSz += tableSz; // ngramCounts[i];
      if (i+1 < lmOrder)
        bowTableSz += tableSz; // ngramCounts[i];
    }
    //probTableSz = Integer.highestOneBit((int)(probTableSz*LOAD_MULTIPLIER))<<1;
    //bowTableSz = Integer.highestOneBit((int)(bowTableSz*LOAD_MULTIPLIER))<<1;
    mprobs = new float[probTableSz];
    mbows = new float[bowTableSz];

    float log10LogConstant = (float)Math.log(10);

    // read in the n-gram tables one by one
    table = new TrieIntegerArrayIndex(ngramCounts[lmOrder-1]/4);
    for (int order = 0; order < lmOrder; order++) {
      System.err.printf("Reading %d %d-grams...\n", ngramCounts[order], order+1);
      String nextOrderHeader = String.format("\\%d-grams:", order+1);
      IString[] ngram = new IString[order+1];
      int[] ngramInts = new int[order+1];

      // skip all material upto the next n-gram table header
      while (!readLineNonNull(reader).startsWith(nextOrderHeader));

      // read in table
      int c=0;
      while (!(inline = readLineNonNull(reader)).equals("")) {
        if(++c % (ngramCounts[order]/10) == 0)
           System.err.printf("  read %d entries...\n",c);
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
        int index = table.insertIntoIndex(ngramInts);
        mprobs[index] = prob;
        if(order+1 < lmOrder) mbows[index] = bow;
      }
    }
    System.err.println("Rehashing... ");
    table.rehash();
    System.err.println("done.");
    table.printInfo();

    // print some status information
    System.gc();
    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err.printf("Done loading arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n", filename, lmOrder,
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

    index = table.getIndex(ngramInts);
    if (index >= 0) { // found a match
      double p = mprobs[index];
      if(verbose)
        System.err.printf("scoreR: seq: %s logp: %f\n", sequence.toString(), p);
      return p;
    }
    if (ngramInts.length == 1) {
      return Double.NEGATIVE_INFINITY; // OOV
    }
    Sequence<IString> prefix = sequence.subsequence(0, ngramInts.length-1);
    int[] prefixInts = Sequences.toIntArray(prefix);
    //if(prefixInts[prefixInts.length-1] == getEndToken().id) {
    //  return Double.NEGATIVE_INFINITY; // end token not in final position
    //}
    index = table.getIndex(prefixInts);
    double bow = 0;
    if (index >= 0) bow = mbows[index];
    if (bow != bow) bow = 0.0; // treat NaNs as bow that are not found at all
    double p = bow + scoreR(sequence.subsequence(1, ngramInts.length));
    if(verbose)
      System.err.printf("scoreR: seq: %s logp: %f bow: %f\n", sequence.toString(), p, bow);
    return p;
  }

  public double score(Sequence<IString> sequence) {
    if(isBoundaryWord(sequence)) return 0.0;
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder = (lmOrder < sequenceSz ? lmOrder : sequenceSz);

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

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > lmOrder-1) return false;
    int[] prefixInts = Sequences.toIntArray(prefix);
    int index = table.getIndex(prefixInts);
    if (index < 0 || index >= mbows.length) return false;
    double bow = mbows[index];
    if (bow == bow) return true;
    return false;
  }

  public int order() {
    return this.lmOrder;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence to score\"\n");
      System.exit(-1);
    }

    verbose = true;
    String model = args[0]; String sent = args[1];
    System.out.printf("Loading lm: %s...\n", model);
    ARPALanguageModel lm = new ARPALanguageModel(model);
    System.out.printf("done loading lm.\n");

    System.out.printf("Sentence: %s\n", sent);
    Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
    System.out.printf("Seq: %s\n", seq);
    double score = LanguageModels.scoreSequence(lm, seq);
    System.out.printf("Sequence score: %f score_log10: %f\n", score, score/Math.log(10));
  }
}
