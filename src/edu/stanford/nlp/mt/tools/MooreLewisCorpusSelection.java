package edu.stanford.nlp.mt.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.KenLanguageModel;
import edu.stanford.nlp.mt.base.LineIndexedCorpus;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

/**
 * Moore-Lewis's "Intelligent Selection of Language Model Training Data" (ACL2010).
 * 
 * @author Thang Luong <lmthang@stanford.edu>
 *
 */
public class MooreLewisCorpusSelection {
  static final int VERBOSE = 1; // Thang Aug13: change from boolean to int, more debugging messages.
  private KenLanguageModel inKenLM;
  private KenLanguageModel outKenLM;
  private LineIndexedCorpus data;

  private IString startToken;
  private IString endToken;
  private int order;
  
  static public void usage() {
    System.err.println("Usage:\n\tjava ...MooreLewisCorpusSelection " +
        "(inDomainKenLM) (outDomainKenLM) (data) (outPrefix) (thresholdString)");
    System.err.println("  outPrefix: for each threshold we will output two files outPrefix.threshold (data) "
        + "and outPrefix.threshold.line (line indices, 0-based)");
    System.err.println("  thresholdString: multiple thresholds could be specified and separated by -, e.g. 0.1-0.2-0.3");
  }

  public MooreLewisCorpusSelection(String inDomainKenLMFile, String outDomainKenLMFile, String dataFile){
    // in-domain KenLM
    System.err.println("# Loading in-domain KenLM " + inDomainKenLMFile);
    inKenLM = new KenLanguageModel(inDomainKenLMFile);

    // out-domain KenLM
    System.err.println("# Loading out-domain KenLM " + outDomainKenLMFile);
    outKenLM = new KenLanguageModel(outDomainKenLMFile);

    // data file
    System.err.println("# Opening %s " + dataFile);
    try {
      data = new LineIndexedCorpus(dataFile);
    } catch (IOException e) {
      System.err.println("! Can't load data file " + dataFile);
      e.printStackTrace();
    }
    
    startToken = inKenLM.getStartToken();
    endToken = inKenLM.getEndToken();
    order = inKenLM.order();
    if(!startToken.equals(outKenLM.getStartToken()) || !endToken.equals(outKenLM.getEndToken()) 
        || order != outKenLM.order()){
      System.err.println("mismatch in either startToken, endToken, or order between in-domain and out-domain KenLMs");
      System.exit(1);
    }
  }

  public void select(String outPrefix, double[] thresholds) throws IOException {
    int numThresholds = thresholds.length;
    PrintWriter[] dataPWs = new PrintWriter[numThresholds];    // data out
    PrintWriter[] linePWs = new PrintWriter[numThresholds];    // line out
    int[] numSelecteds = new int[numThresholds]; 
    for (int i = 0; i < numThresholds; i++) {
      dataPWs[i] = new PrintWriter(new OutputStreamWriter(
          new FileOutputStream(outPrefix + "." + thresholds[i]), "UTF-8"));  

      linePWs[i] = new PrintWriter(new OutputStreamWriter(
          new FileOutputStream(outPrefix + "." + thresholds[i] + ".line"), "UTF-8"));
      
      numSelecteds[i] = 0;
    }
    
    int count=0;
    for (String line : data) {
      // build sequence of IString
      String[] tokens = line.split("\\s+");
      IString[] istrings = new IString[tokens.length+2];
      istrings[0] = startToken; // start token
      for (int i = 0; i < tokens.length; i++) {
        istrings[i+1] = new IString(tokens[i]);
      }
      istrings[istrings.length-1] = endToken; // end token
      Sequence<IString> sequence = new SimpleSequence<IString>(istrings);

      // compute entropy diff = -1/N*log p_in - (-1/N*log p_out) 
      int numNgrams = istrings.length-order+1; // N
      double entropyDiff = -inKenLM.score(sequence)/numNgrams + outKenLM.score(sequence)/numNgrams; 
      for (int i = 0; i < numThresholds; i++) {
        if(entropyDiff<thresholds[i]){
          numSelecteds[i]++;
          dataPWs[i].println(line);
          linePWs[i].println(count); // 0-based index
        }
      }
      
      if(++count % 100000 == 0){
        System.err.print(" (" + count/1000 + "K) ");
      }
    }
    
    System.err.println("Done! Num lines = " + count);
    for (int i = 0; i < numThresholds; i++) {
      dataPWs[i].close();
      linePWs[i].close();
      System.err.println("Threshold " + thresholds[i] + ": selected " + numSelecteds[i]);
    }
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length != 5) {
      System.err.print("Input arguments (count=" + args.length + "):");
      for (String string : args) { System.err.print(" " + string); }
      System.err.println();
      usage();
      System.exit(-1);
    }

    String inDomainKenLMFile = args[0]; // in-domain
    String outDomainKenLMFile = args[1]; // out-domain
    String dataFile = args[2];
    String outPrefix = args[3];
    String thresholdString = args[4];
    MooreLewisCorpusSelection mlcs = new MooreLewisCorpusSelection(inDomainKenLMFile, outDomainKenLMFile, dataFile);
    
    // get thresholds from a string, e.g. 0.1-0.2-0.3
    String[] thresholdTokens = thresholdString.split("-");
    double[] thresholds = new double[thresholdTokens.length];
    System.err.print("# Selecting with thresholds:");
    for (int i = 0; i < thresholdTokens.length; i++) {
      thresholds[i] = Double.parseDouble(thresholdTokens[i]);
      System.err.print(" " + thresholds[i]);
    }
    System.err.println();
    
    // MooreLewis selection
    mlcs.select(outPrefix, thresholds);
  }
}
