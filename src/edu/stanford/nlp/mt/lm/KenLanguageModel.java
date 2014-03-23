package edu.stanford.nlp.mt.lm;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;

/**
 * KenLM language model support via JNI.
 * 
 * @author daniel cer (danielcer@stanford.edu)
 * @author Spence Green
 * @author Kenneth Heafield
 *
 */
public class KenLanguageModel implements LanguageModel<IString> {

  static {
    System.loadLibrary("PhrasalKenLM");
  }

  private final String name;
  private final int order;
  private final long kenLMPtr;

  private AtomicReference<int[]> istringIdToKenLMId;

  private final ReentrantLock preventDuplicateWork = new ReentrantLock();

  // JNI methods
  private native long readKenLM(String filename);
  private native long scoreNGramSeq(long kenLMPtr, int[] ngram, int startIndex);
  private native int getLMId(long kenLMPtr, String token);
  private native int getOrder(long kenLMPtr);

  /**
   * Constructor for multi-threaded queries.
   * 
   * @param filename
   * @param numThreads
   */
  public KenLanguageModel(String filename) {
    name = String.format("KenLM(%s)", filename);
    System.err.printf("KenLM: Reading %s%n", filename);
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
  }

  /**
   * Create the mapping between IString word ids and KenLM word ids.
   */
  private void initializeIdTable() {
    // Don't remove this line!! Sanity check to make sure that start and end load before
    // building the index.
    System.err.printf("Special tokens: start: %s  end: %s%n", TokenUtils.START_TOKEN.toString(),
        TokenUtils.END_TOKEN.toString());
    int[] table = new int[IString.index.size()];
    for (int i = 0; i < table.length; ++i) {
      table[i] = getLMId(kenLMPtr, IString.index.get(i));
    }
    istringIdToKenLMId = new AtomicReference<int[]>(table);
  }

  /**
   * Maps the IString id to a kenLM id. If the IString
   * id is out of range, update the vocab mapping.
   * @param token
   * @return kenlm id of the string
   */
  private int toKenLMId(IString token) {
    {
      int[] map = istringIdToKenLMId.get();
      if (token.id < map.length) {
        return map[token.id];
      }
    }
    // Rare event: we have to expand the vocabulary.
    // In principle, this doesn't need to be a lock, but it does
    // prevent unnecessary work duplication.
    if (preventDuplicateWork.tryLock()) {
      // This thread is responsible for updating the mapping.
      try {
        // Maybe another thread did the work for us?
        int[] oldTable = istringIdToKenLMId.get();
        if (token.id < oldTable.length) {
          return oldTable[token.id];
        }
        int[] newTable = new int[IString.index.size()];
        System.arraycopy(oldTable, 0, newTable, 0, oldTable.length);
        for (int i = oldTable.length; i < newTable.length; ++i) {
          newTable[i] = getLMId(kenLMPtr, IString.index.get(i));
        }
        istringIdToKenLMId.set(newTable);
        return newTable[token.id];
      } finally {
        preventDuplicateWork.unlock();
      }
    }
    // Another thread is working.  Lookup directly.
    return getLMId(kenLMPtr, token.toString());
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
  
  @Override
  public LMState score(Sequence<IString> sequence, int startIndex, LMState priorState) {
    Sequence<IString> boundaryState = ARPALanguageModel.isBoundaryWord(sequence);
    if (boundaryState != null) {
      return new KenLMState(0.0, makeKenLMInput(boundaryState, new int[0]), boundaryState.size());
    }
    
    // Extract prior state
    int[] state = priorState == null ? new int[0] : ((KenLMState) priorState).getState();
    int[] ngramIds = makeKenLMInput(sequence, state);
    
    // Reverse the start index for KenLM
    int kenLMStartIndex = ngramIds.length - state.length - startIndex - 1;
    
    // Execute the query (via JNI) and construct the return state
    long got = scoreNGramSeq(kenLMPtr, ngramIds, kenLMStartIndex);
    float score = Float.intBitsToFloat((int)(got & 0xffffffff));
    int stateLength = (int)(got >> 32);
    return new KenLMState(score, ngramIds, stateLength);
  }
  
  /**
   * Convert a Sequence and an optional state to an input for KenLM.
   * 
   * @param sequence
   * @param priorState
   * @param priorStateLength
   * @return
   */
  private int[] makeKenLMInput(Sequence<IString> sequence, int[] priorState) {
    final int sequenceSize = sequence.size();
    int[] ngramIds = new int[sequenceSize + priorState.length];
    if (priorState.length > 0) {
      System.arraycopy(priorState, 0, ngramIds, sequenceSize, priorState.length);
    }
    for (int i = 0; i < sequenceSize; i++) {
      // Notice: ngramids are in reverse order vv. the Sequence
      ngramIds[sequenceSize-1-i] = toKenLMId(sequence.get(i));
    }
    return ngramIds;
  }
}
