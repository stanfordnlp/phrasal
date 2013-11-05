package edu.stanford.nlp.mt.lm;

import java.io.File;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
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
  
  private int[] istringIdToKenLMId;
  
  // JNI methods
  private native long readKenLM(String filename);
  private native KenLMState scoreNGram(long kenLMPtr, int[] ngram);
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
    initializeIdTable();
  }
  
  private void initializeIdTable() {
    // Don't remove this line!! Sanity check to make sure that start and end load before
    // building the index.
    System.err.printf("Special tokens: start: %s  end: %s%n", TokenUtils.START_TOKEN.toString(),
        TokenUtils.END_TOKEN.toString());
    istringIdToKenLMId = new int[IString.index.size()];
    for (int i = 0; i < istringIdToKenLMId.length; ++i) {
      istringIdToKenLMId[i] = getId(kenLMPtr, IString.index.get(i));
    }
  }
  
  // TODO(spenceg): This should not happen unless we start adding
  // target-side vocabulary on the fly (i.e., after the bitext has
  // been loaded).
  private synchronized void updateIdTable() {
    int[] newTable = new int[IString.index.size()];
    System.arraycopy(istringIdToKenLMId, 0, newTable, 0, istringIdToKenLMId.length);
    for (int i = istringIdToKenLMId.length; i < newTable.length; ++i) {
      newTable[i] = getId(kenLMPtr, IString.index.get(i));
    }
    istringIdToKenLMId = newTable;
  }
  
  private int toKenLMId(IString token) {
    if (token.id >= istringIdToKenLMId.length) {
      updateIdTable();
    }
    return istringIdToKenLMId[token.id];
  }
  
  static <T> Sequence<T> clipNgram(Sequence<T> sequence, int order) {
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);
    return sequenceSz == maxOrder ? sequence :
      sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
  }
  
  private int[] toKenLMIds(Sequence<IString> ngram) {
    int[] ngramIds = new int[ngram.size()];
    for (int i = 0; i < ngramIds.length; i++) {
      // Notice: ngramids are in reverse order vv. the Sequence
      ngramIds[ngramIds.length-1-i] = toKenLMId(ngram.get(i));
    }
    return ngramIds;
  }

  @Override
  public LMState score(Sequence<IString> sequence) {
    Sequence<IString> boundaryState = ARPALanguageModel.isBoundaryWord(sequence);
    if (boundaryState != null) {
      return new KenLMState(0.0, toKenLMIds(boundaryState));
    }
    Sequence<IString> ngram = clipNgram(sequence, order);
    int[] ngramIds = toKenLMIds(ngram);
    KenLMState state = scoreNGram(kenLMPtr, ngramIds);
    return state;
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
