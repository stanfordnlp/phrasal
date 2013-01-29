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
  
  private static final IString START_TOKEN = new IString("<s>");
  private static final IString END_TOKEN = new IString("</s>");
  
  private native long readKenLM(String filename);
  private native double scoreNGram(long kenLMPtr, String[] ngram, boolean firstIsStartToken, boolean lastIsEndToken);
  private native boolean relevantPrefixGram(long kenLMPtr, String[] ngram, boolean firstIsStartToken, boolean lastIsEndToken);
  
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
  
  private String[] ngramStr(Sequence<IString> ngram) {
    String[] ngramStr = new String[ngram.size()];
    for (int i = 0; i < ngramStr.length; i++) {
      ngramStr[i] = ngram.get(i).toString();
    }
    return ngramStr;
  }

  @Override
  public double score(Sequence<IString> sequence) {
    if (ARPALanguageModel.isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngram = clipNgram(sequence, order);
    String[] ngramStr = ngramStr(ngram);       
    boolean firstIsStartToken = ngram.get(0).id == START_TOKEN.id;
    boolean lastIsEndToken = ngram.get(ngramStr.length-1).id == END_TOKEN.id;
    return LOG_10*scoreNGram(kenLMPtr, ngramStr, firstIsStartToken, lastIsEndToken);
  }
      
  @Override
  public boolean releventPrefix(Sequence<IString> sequence) {
    if (sequence.size() > order - 1) {
      return false;
    }
    Sequence<IString> ngram = clipNgram(sequence, order);
    String[] ngramStr = ngramStr(ngram);       
    boolean firstIsStartToken = ngram.get(0).id == START_TOKEN.id;
    boolean lastIsEndToken = ngram.get(ngramStr.length-1).id == END_TOKEN.id;

    return relevantPrefixGram(kenLMPtr, ngramStr, firstIsStartToken, lastIsEndToken);    
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
  
  static {
    System.loadLibrary("PhrasalKenLM");
  }
}
