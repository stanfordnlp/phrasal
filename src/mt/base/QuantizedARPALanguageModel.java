package mt.base;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Loads quantized ARPA language models generated using IRSTLM's quantize-lm.
 * 
 * @author Daniel Cer
 * @author Michel Galley (quantization)
 */
public class QuantizedARPALanguageModel extends ARPALanguageModel {

  private static float log10LogConstant = (float)Math.log(10);
  private float[][] probClusters, bowClusters;
  private byte[][] qprobs, qbows;

  protected QuantizedARPALanguageModel(String filename) throws IOException {
    super(filename);
  }

  protected void init(String filename) throws IOException {
    File f = new File(filename);
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

    LineNumberReader reader = (filename.endsWith(".gz") ? new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)))) :
            new LineNumberReader(new FileReader(f)));

    // read header:
    String line="";
    while(!line.startsWith("qARPA ")) line = readLineNonNull(reader);
    String[] header = line.split("\\s+");
    int hOrder = Integer.parseInt(header[1]);
    assert(hOrder+2 == header.length);
    int[] hBins = new int[hOrder];
    for(int i=0; i<hBins.length; ++i)
      hBins[i] = Integer.parseInt(header[i+2]); 

    // then skip everything until the line that begins with '\data\'
    while (!readLineNonNull(reader).startsWith("\\data\\"));

    // read in ngram counts
    int[] ngramCounts = new int[MAX_GRAM];
    String inline;
    int maxOrder = 0;
    while ((inline = readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("\\s*=\\s*");
      int ngramOrder = Integer.parseInt(fields[0]);
      if (ngramOrder > MAX_GRAM) {
        throw new RuntimeException(String.format("Max n-gram order: %d\n", MAX_GRAM));
      }
      ngramCounts[ngramOrder-1] = Integer.parseInt(fields[1].replaceFirst("[^0-9].*$", ""));
      if (maxOrder < ngramOrder) maxOrder = ngramOrder;
    }
    assert(maxOrder == hOrder);

    tables = new FixedLengthIntegerArrayRawIndex[maxOrder];
    qprobs = new byte[maxOrder][];
    qbows = new byte[maxOrder-1][];
    probClusters = new float[maxOrder][];
    bowClusters = new float[maxOrder-1][];
    
    for (int i = 0; i < maxOrder; i++) {
      int tableSz = Integer.highestOneBit((int)(ngramCounts[i]*LOAD_MULTIPLIER))<<1;
      tables[i] = new FixedLengthIntegerArrayRawIndex(i+1, Integer.numberOfTrailingZeros(tableSz));
      qprobs[i] = new byte[tableSz];
      if (i+1 < maxOrder) qbows[i]  = new byte[tableSz];
    }

    // read in the n-gram tables one by one
    for (int order = 0; order < maxOrder; order++) {
      String nextOrderHeader = String.format("\\%d-grams:", order+1);
      IString[] ngram = new IString[order+1];
      int[] ngramInts = new int[order+1];

      // skip all material upto the next n-gram table header
      while (!readLineNonNull(reader).startsWith(nextOrderHeader));
      
      // Read cluster centers:
      int bins = Integer.parseInt(readLineNonNull(reader));
      assert(bins == hBins[order]);
      System.err.printf("Reading %d cluster centers for %d-grams.\n",bins,order+1);
      probClusters[order] = new float[bins];
      if(order+1 < maxOrder)
        bowClusters[order] = new float[bins];
      for(int i=0; i<bins; ++i) {
        String[] s = readLineNonNull(reader).split("\\s+");
        if(order+1 < maxOrder) {
          assert(s.length == 2);
          bowClusters[order][i] = Float.parseFloat(s[1]);
        } else
          assert(s.length == 1);
        probClusters[order][i] = Float.parseFloat(s[0]);
      }

      // read in table
      System.err.printf("Reading %d %d-grams.\n", qprobs[order].length, order+1);
      while (!(inline = readLineNonNull(reader)).equals("")) {
        if(inline.startsWith("\\end\\"))
          break;
        // during profiling, 'split' turned out to be a bottle neck
        // and using StringTokenizer is about twice as fast
        StringTokenizer tok = new StringTokenizer(inline);
        int prob = Integer.parseInt(tok.nextToken());
				if(prob > Character.MAX_VALUE) {
          throw new RuntimeException(String.format("Token (%d/%d) not a byte at line: \"%s\"", prob, Character.MAX_VALUE, inline));
        }

        for (int i = 0; i <= order; i++) {
          ngram[i] = new IString(tok.nextToken());
          ngramInts[i] = ngram[i].getId();
        }

        int index = tables[order].insertIntoIndex(ngramInts);
        qprobs[order][index] = (byte) prob;
        if(order < qbows.length)  {
					int bow = Integer.parseInt(tok.nextToken());
					assert(bow <= Character.MAX_VALUE);
					qbows[order][index] = (byte) bow;
			  }
      }
    }

    System.gc();

    // print some status information
    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err.printf("Done loading quantized arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n", filename, maxOrder,
            (postLMLoadMemUsed - preLMLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);
    reader.close();
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
    int order = ngramInts.length-1;

    int index = tables[order].getIndex(ngramInts);
    if (index >= 0) { // found a match
      // mg2008: the "& 0xFF" is needed because Java doesn't allow "unsigned byte" 
      double p = probClusters[order][qprobs[order][index] & 0xFF]*log10LogConstant;
      if(verbose)
        System.err.printf("q-scoreR: seq: %s logp: %f (order=%d probCenter=%d)\n", sequence.toString(), p, order, qprobs[order][index]);
      return p;
    }
    if (ngramInts.length == 1) {
      return Double.NEGATIVE_INFINITY; // OOV
    }
    Sequence<IString> prefix = sequence.subsequence(0, ngramInts.length-1);
    int[] prefixInts = Sequences.toIntArray(prefix);
    int order2 = prefixInts.length-1;
    index = tables[order2].getIndex(prefixInts);
    double bow = 0;
    // mg2008: the "& 0xFF" is needed because Java doesn't allow "unsigned byte"
    if (index >= 0) bow = bowClusters[order2][qbows[order2][index] & 0xFF]*log10LogConstant;
    double p = bow + scoreR(sequence.subsequence(1, ngramInts.length));
    if(verbose)
      System.err.printf("q-scoreR: seq: %s logp: %f bow: %f\n", sequence.toString(), p, bow);
    return p;
  }

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > tables.length-1) return false;
    int[] prefixInts = Sequences.toIntArray(prefix);
    int index = tables[prefixInts.length-1].getIndex(prefixInts);
    if (index < 0) return false;
    return true;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) (file to score) (quantized?)\n");
      System.exit(-1);
    }

    verbose = true;
    String model = args[0]; String file = args[1]; boolean quantized = Boolean.parseBoolean(args[2]);
    System.out.printf("Loading lm: %s...\n", model);
    ARPALanguageModel lm = quantized ? new QuantizedARPALanguageModel(model) : new ARPALanguageModel(model);
    System.out.printf("done loading lm.\n");

    for(String sent : ObjectBank.getLineIteratorObjectBank(file)) {
      sent = sent.toLowerCase();
      System.out.printf("Sentence: %s\n", sent);
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      System.out.printf("Seq: %s\n", seq);
      double score = LanguageModels.scoreSequence(lm, seq);
      System.out.printf("Sequence score: %f score_log10: %f\n", score, score/Math.log(10));
    }
  }
}
