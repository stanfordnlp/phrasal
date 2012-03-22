package edu.stanford.nlp.mt.base;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

import net.kotek.jdbm.*;

public class DiskLM implements LanguageModel<IString> {
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");
  public static final IString UNK_TOKEN = new IString("<unk>");
  protected final String name;
  protected final Map<Integer,Integer> ngramOrder;
  protected final Map<String, ProbBowPair> ngramProbBows;
  protected final DB db; 
  protected final int order;
  static public final boolean verbose = false;
  
  public DiskLM(String filename) {
    this.name = String.format("DiskLM(%s)", filename);
    db = new DBMaker(filename).build();
    ngramOrder = db.getHashMap("ngramOrder");
    ngramProbBows = db.getHashMap("ngramProbBows");
    order = ngramProbBows.keySet().size();
  }
  
  @Override
  public IString getStartToken() {
    return START_TOKEN;
  }

  @Override
  public IString getEndToken() {
    return END_TOKEN;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int order() {
    return order;
  }

  @Override
  public double score(Sequence<IString> sequence) {
    if (ARPALanguageModel.isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngramSeq;
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngramSeq = sequence;
    } else {
      ngramSeq = sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
    }

    double score = scoreR(ngramSeq.toString(" "));
    if (verbose)
      System.err.printf("score: seq: %s logp: %f [%f]\n", sequence.toString(),
          score, score / Math.log(10));
    return score;
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
  protected double scoreR(String ngram) {
    if (verbose) {
      System.err.printf("Looking up %s\n", ngram);
    }
    ProbBowPair pbp = ngramProbBows.get(ngram);
    if (pbp != null) { // match found
      if (verbose)
        System.err.printf("EM: scoreR: seq: %s logp: %f\n", ngram, pbp.p);
      return pbp.p;
    }
    
    int nxtSpace = ngram.indexOf(" ");
    if (nxtSpace < 0) {
      return Double.NEGATIVE_INFINITY; // OOV
    }
    int rnxtSpace = ngram.lastIndexOf(" ");
    String prefix = ngram.substring(0, rnxtSpace);
    ProbBowPair prefixPbp = ngramProbBows.get(prefix);
    double bow;
    if (prefixPbp == null) {
      bow = 0.0;
    } else {
      bow = prefixPbp.bow;
    }
    
    double p = bow + scoreR(ngram.substring(nxtSpace+1));
  
    if (verbose)
      System.err.printf("scoreR: seq: %s logp: %f [%f] bow: %f\n",
         ngram, p, p / Math.log(10), bow);
  
    return p;
  }
  
  @Override
  public boolean releventPrefix(Sequence<IString> sequence) {
    return ngramProbBows.containsKey(sequence.toString(" "));
  }
  
  
  public static void ARPAtoDISKLM(String arpaLM, String diskLM) throws IOException {
    File arpaf = new File(arpaLM);
    File diskLMf = new File(diskLM);
    if (diskLMf.exists()) {
      diskLMf.delete();
    }
    
    DB db = new DBMaker(diskLM).build();
    
    Map<Integer,Integer> ngramOrder = db.createHashMap("ngramOrder");
    Map<String, ProbBowPair> ngramProbBows = db.createHashMap("ngramProbBows");
    
    LineNumberReader reader = (arpaLM.endsWith(".gz") ? new LineNumberReader(
        new InputStreamReader(
            new GZIPInputStream(new FileInputStream(arpaLM))))
        : new LineNumberReader(new FileReader(arpaf)));

    // skip everything until the line that begins with '\data\'
    while (!ARPALanguageModel.readLineNonNull(reader).startsWith("\\data\\")) {
    }

    
    String inline;
    int maxOrder = 0;
    while ((inline = ARPALanguageModel.readLineNonNull(reader)).startsWith("ngram")) {
      inline = inline.replaceFirst("ngram\\s+", "");
      String[] fields = inline.split("=");
      int ngramOrderN = Integer.parseInt(fields[0]);
      int ngramOrderCnt = Integer.parseInt(fields[1].replaceFirst(
          "[^0-9].*$", ""));
      ngramOrder.put(ngramOrderN, ngramOrderCnt);
      maxOrder++;
    }

    float log10LogConstant = (float) Math.log(10);

    // read in the n-gram tables one by one
    for (int order = 0; order < maxOrder; order++) {
      System.err.printf("Reading %d %d-grams...\n", ngramOrder.get(order+1),
          order + 1);
      String nextOrderHeader = String.format("\\%d-grams:", order + 1);

      // skip all material upto the next n-gram table header
      while (!ARPALanguageModel.readLineNonNull(reader).startsWith(nextOrderHeader)) {
      }
      
      // read in table
      while (!(inline = ARPALanguageModel.readLineNonNull(reader)).equals("")) {
        // during profiling, 'split' turned out to be a bottle neck
        // and using StringTokenizer is about twice as fast
        StringTokenizer tok = new StringTokenizer(inline);
        float prob = Float.parseFloat(tok.nextToken()) * log10LogConstant;

        StringBuilder ngramBuilder = new StringBuilder();
        
        for (int i = 0; i <= order; i++) {
          ngramBuilder.append(tok.nextToken());
          if (i != order) ngramBuilder.append(" ");
        }
       
        String ngram = ngramBuilder.toString();
        
        float bow = (tok.hasMoreElements() ? Float.parseFloat(tok.nextToken())
            * log10LogConstant : 0);
        
        ProbBowPair pbp = new ProbBowPair(prob, bow);
        ngramProbBows.put(ngram,  pbp);
      }
    }
    db.commit();
    db.close();
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.printf("Usage:\n\t java ...DiskLM (arpalm) (new_disklm)\n");
      System.exit(-1);
    }
    
    String arpaLM = args[0];
    String diskLM = args[1];
    ARPAtoDISKLM(arpaLM, diskLM);
  }
}

class ProbBowPair implements Serializable {
  private static final long serialVersionUID = 1L;
  public final float p;
  public final float bow;
  public ProbBowPair(float p, float bow) {
    this.p = p;
    this.bow = bow;
  }
}
