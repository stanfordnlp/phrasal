package edu.stanford.nlp.mt.lm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface to the Neural Probabilistic Language Model (NPLM) by ISI.
 * 
 * @author Thang Luong
 *
 */
public class NPLM {

  static {
    System.loadLibrary("PhrasalNPLM");
  }

  private final int order;
  private final long nplmPtr;

  private final int bos, eos;

  // JNI methods
  private native long readNPLM(String filename, long nplm_cache_size, int mini_batch_size);
  private native double scoreNgram(long nplmPtr, int[] ngram);
  private native double[] scoreNgrams(long nplmPtr, int[] ngrams, int numNgrams);
  private native long marshalledScoreNGramNPLM(long nplmPtr, int[] ngram);
  private native int getNPLMId(long nplmPtr, String token);
  private native int getNPLMOrder(long nplmPtr);
  
  private int miniBatchSize;
  
  /**
   * Constructor for multi-threaded queries.
   * 
   * @param filename
   * @param numThreads
   */
  public NPLM(String filename, long cacheSize, int miniBatchSize) {
    System.err.printf("# NPLM.java: reading %s\n", (new File(filename)).getAbsolutePath());
    this.miniBatchSize = miniBatchSize;
    if (0 == (nplmPtr = readNPLM(filename, cacheSize, miniBatchSize))) {
      File f = new File(filename);
      if (!f.exists()) {
        new RuntimeException(String.format("Error loading %s - file not found", filename));
      } else {
        new RuntimeException(String.format("Error loading %s - file is likely corrupt or created with an incompatible version of kenlm", filename));
      } 
    }
    
    order = getNPLMOrder(nplmPtr);
    bos = nplmIndex("<s>");
    eos = nplmIndex("</s>");
  }

//  public NPLM(String filename, long cacheSize) {
//    this(filename, cacheSize, 1);
//  }
  
  /**
   * Maps a String to a KenLM id.
   */
  public int nplmIndex(String token) {
    return getNPLMId(nplmPtr, token);
  }
  
  public int BeginSentence() { return bos; }
  public int EndSentence() { return eos; }

  public int order() {
    return order;
  }


  /**
   * Score multiple ngrams
   */
  public double[] scoreNgrams(int[][] ngrams) {
    int numNgrams = ngrams.length;
    if (numNgrams<=0) { 
      return new double[0];
    } else if (numNgrams<=miniBatchSize){
      return scoreNgrams(nplmPtr, NPLM.toOneDimArray(ngrams), numNgrams);  
    } else { // more than miniBatchSize
      List<int[][]> batches = NPLM.splitIntoBatches(ngrams, miniBatchSize);
      double[] scores = new double[numNgrams];
      int count = 0;
      for (int[][] batch : batches) {
        double[] batchScores = scoreNgrams(nplmPtr, NPLM.toOneDimArray(batch), batch.length);
//        System.err.println("Scores for batch " + NPLM.sprint(batch) + ": " + Arrays.toString(batchScores));
        for (double score : batchScores) { scores[count++] = score; }
      }
      assert(count==numNgrams);
      
      return scores;
    }
  }
  
  /**
   * Return only the score.
   */
  public double scoreNgram(int[] ngram) {
    return scoreNgram(nplmPtr, ngram);
  }
  
  /**
   * Split ngrams into mini batches.
   * 
   * @param ngrams
   * @param miniBatchSize
   * @return
   */
  public static List<int[][]> splitIntoBatches(int[][] ngrams, int miniBatchSize){
    int numNgrams = ngrams.length;
    if(numNgrams<=0) { throw new RuntimeException("! Empty ngrams."); }
    
    int numBatches = (numNgrams-1)/miniBatchSize + 1;
    List<int[][]> batches = new ArrayList<int[][]>();
    
    for(int batchId=0; batchId<numBatches; batchId++){ // score one batch at a time
      int startId = batchId*miniBatchSize;
      int endId = (batchId+1)*miniBatchSize-1;
      if (batchId==(numBatches-1)) { // last batch
        endId =  numNgrams-1;
      }
      
      // setting up ngrams for a batch
      int currentBatchSize = endId - startId + 1;
      int[][] batchNgrams = new int[currentBatchSize][];
      for(int i=startId, count=0; i<=endId; i++, count++){
        batchNgrams[count] = ngrams[i];
      }
      
      batches.add(batchNgrams);
    }
    
    return batches;
  }
  
  public static int[] toOneDimArray(int[][] ngrams){
    int numNgrams = ngrams.length;
    if (numNgrams==0) { return new int[0]; }
    int order = ngrams[0].length;
    
    // convert an array of ngrams into a 1-dim array
    int[] ngramIds = new int[numNgrams*order];
    int i=0;
    for(int[] ngram : ngrams){
      for (int j = 0; j < order; j++) {
        ngramIds[i++] = ngram[j];
      }
    }
    
    return ngramIds;
  }
  

  public static String sprint(int[][] values){
    StringBuilder sb = new StringBuilder("[");
    for (int[] value : values) {
      sb.append(Arrays.toString(value) + " ");
    }
    if(values.length>0) { sb.deleteCharAt(sb.length()-1); }
    sb.append("]");
    
    return sb.toString();
  }
  
}

///**
//* Score multiple ngrams
//*/
//public double[] scoreNgrams(List<int[]> ngramList) {
//int numNgrams = ngramList.size();
//
//// convert a list of ngrams into a 1-dim array
//int[] ngramIds = new int[numNgrams*order];
//int i=0;
//for(int[] ngram : ngramList){
// for (int j = 0; j < order; j++) {
//   ngramIds[i++] = ngram[j];
// }
//}
//
//return scoreNgrams(nplmPtr, ngramIds, numNgrams);
//}
