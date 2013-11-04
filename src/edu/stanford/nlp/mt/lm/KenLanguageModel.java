package edu.stanford.nlp.mt.lm;

import java.io.File;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.TokenUtils;

/**
 * KenLanguageModel - KenLM language model support via JNI.
 * 
 * @author daniel cer (danielcer@stanford.edu)
 *
 */
public class KenLanguageModel implements LanguageModel<IString> {
  
  static {
    System.load(System.getProperty("java.library.path") + "/libPhrasalKenLM.so");
  }
  
  final String name;
  final int order;
  private long kenLMPtr;
  
  private final int kenLMStartId;
  private final int kenLMEndId;
  
  // JNI methods
  private native long readKenLM(String filename);
  private native KenLMState scoreNGram(long kenLMPtr, String[] ngram);
  private native int getId(long kenLMPtr, String token);
  private native int getOrder(long kenLMPtr);
  
  public KenLanguageModel(String filename) {
    name = String.format("KenLM(%s)", filename);
    System.err.println("Reading " + filename);
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
    kenLMStartId = getId(kenLMPtr, TokenUtils.START_TOKEN.toString());
    kenLMEndId = getId(kenLMPtr, TokenUtils.END_TOKEN.toString());
  }
  
  static <T> Sequence<T> clipNgram(Sequence<T> sequence, int order) {
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);
    return sequenceSz == maxOrder ? sequence :
      sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
  }
  
  private int[] ngramIds(Sequence<IString> ngram) {
    int[] ngramIds = new int[ngram.size()];
    for (int i = 0; i < ngramIds.length; i++) {
      // Notice: ngramids are in reverse order vv. the Sequence
      ngramIds[ngramIds.length-1-i] = getId(kenLMPtr, ngram.get(i).toString());
    }
    return ngramIds;
  }

  @Override
  public LMState score(Sequence<IString> sequence) {
    Sequence<IString> boundaryState = ARPALanguageModel.isBoundaryWord(sequence);
    if (boundaryState != null) {
      int[] stateIds = boundarySequenceToKenlm(boundaryState);
      return new KenLMState(0.0, stateIds);
    }
    Sequence<IString> ngram = clipNgram(sequence, order);
    KenLMState state = scoreNGram(kenLMPtr, Sequences.toStringArray(ngram));
    return state;
  }
  
  private int[] boundarySequenceToKenlm(Sequence<IString> boundaryState) {
    int size = boundaryState.size();
    int[] tokenIds = new int[size];
    for (int i = 0; i < size; ++i) {
      if (boundaryState.get(i).equals(TokenUtils.START_TOKEN)) {
        tokenIds[i] = kenLMStartId;
      } else if (boundaryState.get(i).equals(TokenUtils.END_TOKEN)) {
        tokenIds[i] = kenLMEndId;
      } else {
        throw new RuntimeException("Non-boundary token in boundary state: " + boundaryState.get(i).toString());
      }
    }
    return tokenIds;
  }
  
  @Override
  public boolean relevantPrefix(Sequence<IString> sequence) {
    // TODO(spenceg): Deprecate this method
    return true;    
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
}
