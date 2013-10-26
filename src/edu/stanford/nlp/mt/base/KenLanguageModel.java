package edu.stanford.nlp.mt.base;

import java.io.File;

import static java.lang.System.*;

/**
 * KenLanguageModel - KenLM language model support via JNI.
 * 
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class KenLanguageModel implements LanguageModel<IString> {
  
  final String name;
  final int order;
  
  private native long readKenLM(String filename);
  
  private native double scoreNGram(long kenLMPtr, int[] ngram);
  private native boolean relevantPrefixGram(long kenLMPtr, int[] ngram);
  private native int getId(long kenLMPtr, String token);
  
  int[] idMap = new int[0];
  
  private int id(IString tok) {
    if (tok.id >= idMap.length) {
      int[] newIdMap = new int[IString.index.size()];
      for (int i = 0; i < idMap.length; i++) {
        newIdMap[i] = idMap[i];
      }
      for (int i = idMap.length; i < newIdMap.length; i++) {
        newIdMap[i] = getId(kenLMPtr, IString.index.get(i));
      }
      idMap = newIdMap;
    }
    
    return idMap[tok.id];
  }
  
  private native int getOrder(long kenLMPtr);
  private long kenLMPtr;
  
  static final float LOG_10 = (float) Math.log(10);

  public KenLanguageModel(String filename) {
    name = String.format("KenLM(%s)", filename);
    err.println("Reading " + filename);
    if (0 == (kenLMPtr = readKenLM(filename))) {
      File f = new File(filename);
      if (!f.exists()) {
        new RuntimeException(String.format("Error loading %s - file not found", filename));
      } else if (f.isDirectory()) {
        new RuntimeException(String.format("Error loading %s - path points to a directory not a file", filename));
      } else {
        new RuntimeException(String.format("Error loading %s - file is likely corrupt or created with an incompatible version of kenlm", filename));
      } 
    }
    order = getOrder(kenLMPtr);    
  }
  
  static <T> Sequence<T> clipNgram(Sequence<T> sequence, int order) {
    Sequence<T> ngram;
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
    }
    return ngram;
  }
  
  private int[] ngramIds(Sequence<IString> ngram) {
    int[] ngramIds = new int[ngram.size()];
    for (int i = 0; i < ngramIds.length; i++) {
      ngramIds[ngramIds.length-1-i] = id(ngram.get(i));
    }
    return ngramIds;
  }

  @Override
  public double score(Sequence<IString> sequence) {
    if (ARPALanguageModel.isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngram = clipNgram(sequence, order);
    int[] ngramIds = ngramIds(ngram);
    return LOG_10*scoreNGram(kenLMPtr, ngramIds);
  }
      
  @Override
  public boolean relevantPrefix(Sequence<IString> sequence) {
    if (sequence.size() > order - 1) {
      return false;
    }
    Sequence<IString> ngram = clipNgram(sequence, order);    
    int[] ngramIds = ngramIds(ngram);       
    
    return relevantPrefixGram(kenLMPtr, ngramIds);    
  }

  @Override
  public IString getStartToken() {
    return TokenUtils.START_TOKEN;
  }

  @Override
  public IString getEndToken() {
    return TokenUtils.END_TOKEN;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int order() {
    return order;
  }
  
  static {
    System.load(System.getProperty("java.library.path") + "/libPhrasalKenLM.so");
    //System.loadLibrary("PhrasalKenLM");
  }
}
