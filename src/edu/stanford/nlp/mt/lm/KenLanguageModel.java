package edu.stanford.nlp.mt.lm;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.mt.util.Vocabulary;

/**
 * KenLM language model support via JNI.
 *
 * @author daniel cer
 * @author Spence Green
 * @author Kenneth Heafield
 *
 */
public class KenLanguageModel implements LanguageModel<IString> {

  private static final Logger logger = LogManager.getLogger(KenLanguageModel.class.getName());
  
  private static final int[] EMPTY_INT_ARRAY = new int[0];
  private static final KenLMState ZERO_LENGTH_STATE = new KenLMState(0.0, EMPTY_INT_ARRAY, 0);
  public static final String KENLM_LIBRARY_NAME = "PhrasalKenLM";
  
  static {
    try {
      System.loadLibrary(KENLM_LIBRARY_NAME);
      logger.info("Loaded KenLM JNI library.");
    
    } catch (java.lang.UnsatisfiedLinkError e) {
      logger.fatal("KenLM has not been compiled!", e);
      System.exit(-1);
    }
  }

  private final KenLM model;

  private final String name;

  private AtomicReference<int[]> istringIdToKenLMId;

  private final ReentrantLock preventDuplicateWork = new ReentrantLock();

  /**
   * Constructor for multi-threaded queries.
   *
   * @param filename
   */
  public KenLanguageModel(String filename) {
    model = new KenLM(filename);
    name = String.format("KenLM(%s)", filename);
    initializeIdTable();
  }

  /**
   * Create the mapping between IString word ids and KenLM word ids.
   */
  private void initializeIdTable() {
    // Don't remove this line!! Sanity check to make sure that start and end load before
    // building the index.
    logger.info("Special tokens: start: {}  end: {}", TokenUtils.START_TOKEN,
        TokenUtils.END_TOKEN);
    int[] table = new int[Vocabulary.systemSize()];
    for (int i = 0; i < table.length; ++i) {
      table[i] = model.index(Vocabulary.systemGet(i));
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
        int[] newTable = new int[Vocabulary.systemSize()];
        System.arraycopy(oldTable, 0, newTable, 0, oldTable.length);
        for (int i = oldTable.length; i < newTable.length; ++i) {
          newTable[i] = model.index(Vocabulary.systemGet(i));
        }
        istringIdToKenLMId.set(newTable);
        return newTable[token.id];
      } finally {
        preventDuplicateWork.unlock();
      }
    }
    // Another thread is working.  Lookup directly.
    return model.index(token.toString());
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
    return model.order();
  }

  @Override
  public LMState score(Sequence<IString> sequence, int startIndex, LMState priorState) {
    if (sequence.size() == 0) {
      // Source deletion rule
      return priorState == null ? ZERO_LENGTH_STATE : priorState;
    }

    // Extract prior state
    int[] state = priorState == null ? EMPTY_INT_ARRAY : ((KenLMState) priorState).getState();
    int[] ngramIds = makeKenLMInput(sequence, state);

    // Reverse the start index for KenLM
    int kenLMStartIndex = ngramIds.length - state.length - startIndex - 1;
    
    // Execute the query (via JNI) and construct the return state
    long got = model.scoreSeqMarshalled(ngramIds, kenLMStartIndex);
    
    return new KenLMState(KenLM.scoreFromMarshalled(got), ngramIds, KenLM.rightStateFromMarshalled(got));
  }

  /**
   * Convert a Sequence and an optional state to an input for KenLM.
   *
   * @param sequence
   * @param priorState
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
  
// TODO(spenceg) This never yielded an improvement....
//  private static final int DEFAULT_CACHE_SIZE = 10000;
//  private static final ThreadLocal<KenLMCache> threadLocalCache =
//      new ThreadLocal<KenLMCache>();
//  
//  private static class KenLMCache {
//    private final long[] keys;
//    private final long[] values;
//    private final int mask;
//    public KenLMCache(int size) {
//      this.keys = new long[size];
//      this.values = new long[size];
//      this.mask = size - 1;
//    }
//    
//    public Long get(int[] kenLMInput, int startIndex) {
//      long hashValue = MurmurHash2.hash64(kenLMInput, kenLMInput.length, startIndex);
//      int k = ideal(hashValue);
//      return keys[k] == hashValue ? values[k] : null;
//    }
//    private int ideal(long hashed) {
//      return ((int)hashed) & mask;
//    }
//    public void insert(int[] kenLMInput, int startIndex, long value) {
//      long hashValue = MurmurHash2.hash64(kenLMInput, kenLMInput.length, startIndex);
//      int k = ideal(hashValue);
//      keys[k] = hashValue;
//      values[k] = value;
//    }
//  }
}
