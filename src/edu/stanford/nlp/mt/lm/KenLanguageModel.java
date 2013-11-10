package edu.stanford.nlp.mt.lm;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;

/**
 * KenLanguageModel - KenLM language model support via JNI.
 * 
 * @author daniel cer (danielcer@stanford.edu)
 * @author Spence Green
 *
 */
public class KenLanguageModel implements LanguageModel<IString> {
  
  static {
    System.loadLibrary("PhrasalKenLM");
  }
  
  // See vocab.cc. <unk> is always first in the vocabulary. If this assumption
  // changes, then this value needs to be updated.
  private static final int UNK_KENLM_ID = 0;
  
  private final String name;
  private final int order;
  private final long kenLMPtr;
  private int[] istringIdToKenLMId;
  private final ReentrantLock indexLock;
 
  // JNI methods
  private native long readKenLM(String filename);
  private native long scoreNGram(long kenLMPtr, int[] ngram);
  private native int getId(long kenLMPtr, String token);
  private native int getOrder(long kenLMPtr);

  /**
   * Constructor for single-threaded usage.
   * 
   * @param filename
   */
  public KenLanguageModel(String filename) {
    this(filename,1);
  }
  
  /**
   * Constructor for multi-threaded queries.
   * 
   * @param filename
   * @param numThreads
   */
  public KenLanguageModel(String filename, int numThreads) {
    name = String.format("KenLM(%s)", filename);
    System.err.printf("KenLM: Reading %s (%d threads)%n", filename, numThreads);
    if (0 == (kenLMPtr = readKenLM(filename))) {
      File f = new File(filename);
      if (!f.exists()) {
        new RuntimeException(String.format("Error loading %s - file not found", filename));
      } else {
        new RuntimeException(String.format("Error loading %s - file is likely corrupt or created with an incompatible version of kenlm", filename));
      } 
    }
    order = getOrder(kenLMPtr);
    initializeIdTable();
    indexLock = new ReentrantLock();
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
  
  /**
   * Extend the IString --> KenLM mapping. This method is *not*
   * threadsafe and is thus called only when <code>indexLock</code>
   * has been acquired.
   */
  private void updateIdTable() {
    int[] newTable = new int[IString.index.size()];
    System.arraycopy(istringIdToKenLMId, 0, newTable, 0, istringIdToKenLMId.length);
    for (int i = istringIdToKenLMId.length; i < newTable.length; ++i) {
      newTable[i] = getId(kenLMPtr, IString.index.get(i));
    }
    istringIdToKenLMId = newTable;
  }

  /**
   * Maps the IString id to a kenLM id. If the IString
   * index is extended, this call tries to acquire a lock on
   * <code>istringIdToKenLMId</code> and extend it. If it cannot,
   * then another thread is updating the table and this method
   * return <code>UNK_KENLM_ID</code>.
   * 
   * @param token
   * @return
   */
  private int toKenLMId(IString token) {
    if (token.id < istringIdToKenLMId.length) {
      return istringIdToKenLMId[token.id];

    } else if (indexLock.tryLock()) {
      updateIdTable();
      indexLock.unlock();
      return istringIdToKenLMId[token.id];

    } else {
      return UNK_KENLM_ID;
    }
  }
  
  private static <T> Sequence<T> clipNgram(Sequence<T> sequence, int order) {
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
    // got is (state_length << 32) | prob where prob is a float.
    long got = scoreNGram(kenLMPtr, ngramIds);
    float score = Float.intBitsToFloat((int)(got & 0xffffffff));
    int state_length = (int)(got >> 32);
    return new KenLMState(score, Arrays.copyOfRange(ngramIds, 0, state_length));
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
