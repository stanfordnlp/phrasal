package edu.stanford.nlp.mt.base;

import java.util.Arrays;

import edu.berkeley.nlp.lm.BackoffLm;
import edu.berkeley.nlp.lm.WordIndexer;
import edu.berkeley.nlp.lm.io.LmReaders;
import edu.berkeley.nlp.lm.map.ConfigOptions;

/**
 * Interface to BerkeleyLM
 * 
 * @author daniel
 *
 */
public class BerkeleyLM implements LanguageModel<IString> {
  final BackoffLm<IString> berkeleylm;
  final int order;  
  final String name;
  IString startToken = ARPALanguageModel.START_TOKEN;
  IString endToken = ARPALanguageModel.START_TOKEN;
  IString unkToken = ARPALanguageModel.UNK_TOKEN;
  static final boolean VERBOSE = false;
  static final double LOG10 = Math.log(10);
  
  @Override
  public double score(Sequence<IString> sequence) {
    int[] ngram = toBerkeleyNgram(sequence);
    double score = (double)berkeleylm.getLogProb(ngram)*LOG10;
    if (VERBOSE) {
      System.out.printf("sequence: %s score: %e\n", sequence, score);
    }
    return score;
  }
  
  private int[] toBerkeleyNgram(Sequence<IString> sequence) {
    int sz = sequence.size();
    int[] ngram = new int[Math.min(sz, order)];
    for (int i = 0; i < ngram.length; i++) {
      IString token = sequence.get(sz-1-i);
      ngram[ngram.length-1-i] = token.id;      
    }
    if (VERBOSE) {
      System.out.printf("sequence: %s int arr: %s\n", sequence, Arrays.toString(ngram));
    }
    return ngram;
  }
              
  @Override
  public IString getStartToken() {
    return startToken;
  }

  @Override
  public IString getEndToken() {
    return endToken;
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
  public boolean releventPrefix(Sequence<IString> sequence) {
    int[] ngramBig, ngramSmall;
    if (sequence.size() >= order) return false;
    ngramBig = new int[sequence.size()];
    ngramSmall = new int[sequence.size()-1];
    ngramBig = toBerkeleyNgram(sequence);
    System.arraycopy(ngramBig, 1, ngramSmall, 0, ngramSmall.length);
    ngramSmall = toBerkeleyNgram(sequence.subsequence(0, ngramBig.length-1));
    return berkeleylm.getLogProb(ngramBig) != berkeleylm.getLogProb(ngramSmall);
  }
  
  public BerkeleyLM(String filename, int order) {
     System.gc();
     Runtime rt = Runtime.getRuntime();
     long preLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
     long startTimeMillis = System.currentTimeMillis();
     // TODOPAULS: since loading the lm takes so long, you might want to give users an optional status msg about how much has been loaded so far
     // TODOPAULS: the argument order only really makes sense as 'maximum order' 
     this.berkeleylm = LmReaders.readArpaLmFile(new ConfigOptions(), filename, order, new IStringWordIndexer());
     //this.order = berkeleylm.getLmOrder();
     int maxTestedOrder = 0;
     
     // TODOPAULS: BerkeleyLM#getLmOrder() should return actual order of ARPA lang model read in
     for (int i = 0; i < ARPALanguageModel.MAX_GRAM; i++) {
       int[] testNgram = new int[i+1];
       try {
         this.berkeleylm.getLogProb(testNgram);
       } catch (ArrayIndexOutOfBoundsException e) {
         break;
       }
       maxTestedOrder = i+1;
     }
     this.order = maxTestedOrder;
     this.name = String.format("BLM(%s)", filename);    
     long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
     long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
     System.err
     .printf(
         "Done loading arpa format n-grams using BerkeleyLM: %s (order: %d) (mem used: %d MiB time: %.3f s)\n",
         filename, order, (postLMLoadMemUsed - preLMLoadMemUsed)
             / (1024 * 1024), loadTimeMillis / 1000.0);     
  }

class IStringWordIndexer implements WordIndexer<IString> {
 
  private static final long serialVersionUID = 1L;

  @Override
  public int getOrAddIndex(IString word) {
    return word.id;
  }

  @Override
  public int getOrAddIndexFromString(String word) {    
    IString iword = new IString(word);
    return iword.id;
  }

  @Override
  public int getIndexPossiblyUnk(IString word) {   
    return word.id;
  }

  @Override
  public IString getWord(int index) {
    return new IString(index);
  }

  @Override
  public int numWords() {
    return IString.index.size();
  }

  @Override
  public IString getStartSymbol() {
    return startToken;    
  }

  @Override
  public void setStartSymbol(IString sym) {
    startToken = sym;    
  }

  @Override
  public IString getEndSymbol() {
    return endToken;
  }

  @Override
  public void setEndSymbol(IString sym) {
    endToken = sym;
  }

  @Override
  public IString getUnkSymbol() {
    return unkToken;
  }

  @Override
  public void setUnkSymbol(IString sym) {
    unkToken = sym;    
  }

  @Override
  public void trimAndLock() {
    // no-op , this index is backed by the common IString index
  }
}


}
