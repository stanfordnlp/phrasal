package edu.stanford.nlp.mt.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.LineIndexedCorpus;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.lm.KenLanguageModel;

/**
 * extension of Moore-Lewis's "Intelligent Selection of Language Model Training Data" (ACL2010)
 * detailed in Domain Adaptation via Pseudo In-Domain Data Selection
 * http://research.microsoft.com/pubs/155466/emnlp11-select-train-data.pdf
 * This also takes into account of the source in addition to the target.
 * @author Sida Wang <sidaw@stanford.edu> based on MooreLewis by
 * @author Thang Luong <lmthang@stanford.edu>
 */

public class ModifiedMooreLewisCorpusSelection {
  private KenLanguageModel inKenLMSource;
  private KenLanguageModel inKenLMTarget;
  private KenLanguageModel outKenLMSource;
  private KenLanguageModel outKenLMTarget;
  
  private LineIndexedCorpus dataSource;
  private LineIndexedCorpus dataTarget;
  private double[] perpDiffScores; // cross-entropy diff scores
  
  private IString startToken;
  private IString endToken;
  private int order;
  
  static public void usage() {
    System.err.println("Usage:\n\tjava ...ModifiedMooreLewisCorpusSelection " +
        "(selectionSize) (inDomainKenLMPrefix) (outDomainKenLMPredix) (dataPrefix) (outPrefix) "
        + "[lenThreshold] [isRemoveRepetition] [targetWeight]");
    System.err.println("  (in|out)DomainKenLMPrefix.src and (in|out)inDomainKenLMPrefix.targ are expected");
    System.err.println("  outPrefix: for each selectSize we will output three files outPrefix.data, "
        + "outPrefix.score (cross-entropy diff scores), and outPrefix.line (0-based line indices)");
    System.err.println("  lenThreshold: only select sentences with >= lenThreshold tokens (default=1)");
    System.err.println("  isRemoveRepetition: if set to true, only keep non-duplicated sentences (default=false)");
  }

  public ModifiedMooreLewisCorpusSelection(String inDomainKenLMFilePrefix,
		  String outDomainKenLMFilePrefix, String dataFilePrefix){
    // in-domain KenLM
	
	String inDomainKenLMFileSource = inDomainKenLMFilePrefix + ".src";
	String inDomainKenLMFileTarget = inDomainKenLMFilePrefix + ".targ";
    System.err.println("# Loading in-domain KenLM " + inDomainKenLMFileSource);
    inKenLMSource = new KenLanguageModel(inDomainKenLMFileSource);
    System.err.println("# Loading in-domain KenLM " + inDomainKenLMFileTarget);
    inKenLMTarget = new KenLanguageModel(inDomainKenLMFileTarget);

    // out-domain KenLM
    String outDomainKenLMFileSource = outDomainKenLMFilePrefix + ".src";
    String outDomainKenLMFileTarget = outDomainKenLMFilePrefix + ".targ";
    System.err.println("# Loading out-domain KenLM " + outDomainKenLMFileSource);
    outKenLMSource = new KenLanguageModel(outDomainKenLMFileSource);
    System.err.println("# Loading out-domain KenLM " + outDomainKenLMFileTarget);
    outKenLMTarget = new KenLanguageModel(outDomainKenLMFileTarget);

    checkConsistency(inKenLMSource, outKenLMSource);
    checkConsistency(inKenLMTarget, outKenLMTarget);
    
    // data file
    String dataFileSource = dataFilePrefix + ".src";
    String dataFileTarget = dataFilePrefix + ".targ";
    System.err.println("# Opening " + dataFileSource);
    try {
      dataSource = new LineIndexedCorpus(dataFileSource);
      dataTarget = new LineIndexedCorpus(dataFileTarget);
    } catch (IOException e) {
      System.err.println("! Can't load data file " + dataFilePrefix);
      e.printStackTrace();
    }
    
    assert dataSource.size() == dataTarget.size():
    	"bitexts should have the same size on both source and target";
    // cross-entropy diff scores
    perpDiffScores = new double[dataSource.size()];
    
    // others
  }
  
  private void checkConsistency(KenLanguageModel inKenLM, KenLanguageModel outKenLM) {
	startToken = inKenLM.getStartToken();
	endToken = inKenLM.getEndToken();
	order = inKenLM.order();
	if(!startToken.equals(outKenLM.getStartToken()) || !endToken.equals(outKenLM.getEndToken()) 
	   || order != outKenLM.order()){
	  System.err.println("mismatch in either startToken, endToken, or order between in-domain and out-domain KenLMs");
	  System.exit(1);
	}
  }

  // smallest values first
  class SentenceScoreComparator implements Comparator<Integer> {
    @Override
    public int compare(Integer o1, Integer o2) {         
       return (int)Math.signum(perpDiffScores[o1]-perpDiffScores[o2]);
    }      
  }
  
  public double computePerpDiff(String lineSource, String lineTarget, double targetWeight){
    double scoreTarget = computeSinglePerpDiff(lineTarget.split("\\s+"), inKenLMTarget, outKenLMTarget);
    double scoreSource = computeSinglePerpDiff(lineSource.split("\\s+"), inKenLMSource, outKenLMSource);
    return targetWeight*scoreTarget + (1-targetWeight)*scoreSource;
  }
  
  // (in|out)KenLM can be either source or target
  public double computeSinglePerpDiff(String[] tokens, KenLanguageModel inKenLM, KenLanguageModel outKenLM){
    // build sequence of IString
    Sequence<IString> seq = new SimpleSequence<IString>(true, IStrings.toIStringArray(tokens));
    Sequence<IString> sequence = Sequences.wrapStartEnd(seq, startToken, endToken);

    // compute entropy diff = -1/N*log p_in - (-1/N*log p_out) 
    int numNgrams = (sequence.size()<order)?1 : (sequence.size()-order+1); // N
    return Math.exp(-inKenLM.score(sequence, 1, null).getScore()/numNgrams) - Math.exp(-outKenLM.score(sequence, 1, null).getScore()/numNgrams);
  }
  
  public void select(String outPrefix, int selectionSize, 
		  int lenThreshold, boolean isRemoveRepetition, double targetWeight) throws IOException {
    PriorityQueue<Integer> Q = new PriorityQueue<Integer>(perpDiffScores.length, new SentenceScoreComparator());
    int i = 0;
    for (; i<dataTarget.size(); i++) {
      String[] tokens = dataTarget.get(i).split("\\s+");
      if(tokens.length>=lenThreshold){ // >= lenThreshold tokens
        perpDiffScores[i] = computePerpDiff(dataSource.get(i), dataTarget.get(i), targetWeight);
        Q.add(i);
      }
      if((i+1) % 100000 == 0){
        System.err.print(" (" + i/1000 + "K) ");
      }
    }    
    System.err.println("Done! Num lines = " + i);

    // init print writers
    System.err.println("# Output sorted cross-entropy diff scores ...");
    PrintWriter selectedDataPWSource = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(outPrefix + ".data.source"), "UTF-8"));  
    PrintWriter selectedDataPWTarget = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(outPrefix + ".data.target"), "UTF-8"));  
    
    PrintWriter selectedScorePW = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(outPrefix + ".score"), "UTF-8"));  
    PrintWriter selectedLinePW = new PrintWriter(new OutputStreamWriter(
        new FileOutputStream(outPrefix + ".line"), "UTF-8"));

    // picking up smallest cross-entropy diff values first
    i = 0;
    Set<String> uniqueLines = new HashSet<String>(); 
    while(!Q.isEmpty()){
      int lineId = Q.poll();
      String lineSource = dataSource.get(lineId);
      String lineTarget = dataTarget.get(lineId);
      String lineCombined = lineSource + "___" + lineTarget;
      if(!isRemoveRepetition || !uniqueLines.contains(lineCombined)){ // if isRemoveRepetition=true, line must be unique
    	selectedDataPWSource.println(lineSource);
    	selectedDataPWTarget.println(lineTarget);
        selectedScorePW.println(perpDiffScores[lineId]);
        selectedLinePW.println(lineId); // 0-based index
        
        if(!isRemoveRepetition)
        	uniqueLines.add(lineCombined);
        
        i++;
        if(i==selectionSize){
          break;
        }
      }
    }
    
    selectedDataPWSource.close();
    selectedDataPWTarget.close();
    selectedScorePW.close();
    selectedLinePW.close();
  }
  
  public static void main(String[] args) throws IOException {
    if (args.length<5 || args.length>8) {
      System.err.print("Input arguments (count=" + args.length + "):");
      for (String string : args) { System.err.print(" " + string); }
      System.err.println();
      usage();
      System.exit(-1);
    }

    int selectionSize = Integer.parseInt(args[0]);
    String inDomainKenLMFile = args[1]; // in-domain
    String outDomainKenLMFile = args[2]; // out-domain
    String dataFile = args[3];
    String outPrefix = args[4];
    int lenThreshold = (args.length>=6)? Integer.parseInt(args[5]):1; // select sentences >= lenThreshold tokens
    boolean isRemoveRepetition = (args.length==7)? Boolean.parseBoolean(args[6]):false;
    double targetWeight = (args.length==8)? Double.parseDouble(args[7]):0.5;
    
    ModifiedMooreLewisCorpusSelection mmlcs = new ModifiedMooreLewisCorpusSelection(inDomainKenLMFile, outDomainKenLMFile, dataFile);
    
    // MooreLewis selection
    mmlcs.select(outPrefix, selectionSize, lenThreshold, isRemoveRepetition, targetWeight);
  }
}
