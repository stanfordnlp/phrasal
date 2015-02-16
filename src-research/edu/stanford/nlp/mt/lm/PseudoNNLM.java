/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Pretend like an NNLM but is backed by KenLM. For debugging purposes.
 * Mimic KenLanguageModel
 * 
 * @author Thang Luong
 *
 */
public class PseudoNNLM extends AbstractNNLM {
  static {
    System.loadLibrary("PhrasalKenLM");
  }
  private KenLM model;
  private AtomicReference<int[]> istringIdToKenLMId;
  private ConcurrentHashMap<Integer, Integer> kenlmIdToIstringId; // since this is a pseudo class we don't need to be so efficient here
  private final ReentrantLock preventDuplicateWork = new ReentrantLock();

  private int startId;
  public PseudoNNLM() {  }

  /**
   * Constructor for multi-threaded queries.
   * 
   * @param filename
   */
  public PseudoNNLM(String filename) {
    model = new KenLM(filename);
    name = String.format("PseudoLM(%s)", filename);
    order = model.order();
    kenlmIdToIstringId = new ConcurrentHashMap<Integer, Integer>();
    initializeIdTable();
    
    startId = toKenLMId(TokenUtils.START_TOKEN);
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
      table[i] = model.index(IString.index.get(i));
      
      // Thang May14: reverse map
      kenlmIdToIstringId.put(table[i], i);
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
          newTable[i] = model.index(IString.index.get(i));
          
          // Thang May14: reverse map
          kenlmIdToIstringId.put(newTable[i], i);
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
  
  // Thang Apr14
  /**
   * @param istringSeq: words in normal order (the number of words should be equal to the LM order.
   * @return score the ngram
   */
  public double score(Sequence<IString> istringSeq) {
    int size = istringSeq.size(); 
    assert(size==order());
    
    int[] ngramIds = new int[size];
    int i = 0;
    for (IString iString : istringSeq) {
      // Notice: ngramids are in reverse order vv. the Sequence
      ngramIds[size-1-i] = toKenLMId(iString);
      i++;
    }
    
    // Execute the query (via JNI) and return the score only
    return model.score(ngramIds);
  }
  
  @Override
  public double scoreNgram(int[] ngram) {
    int[] ngramKenlmIds = new int[ngram.length];
    for (int i = 0; i < ngram.length; i++) { ngramKenlmIds[ngram.length-1-i] = ngram[i]; }
    
    // Execute the query (via JNI) and return the score only
    return model.score(ngramKenlmIds);
  }

  @Override
  public double[] scoreNgrams(int[][] ngrams) {
    double[] scores = new double[ngrams.length];
    for (int i = 0; i < ngrams.length; i++) { scores[i] = scoreNgram(ngrams[i]); }
    return scores;
  }

  /**
   * Extract an ngram. 
   * 
   * @param pos -- tgt position of the last word in the ngram to be extracted (should be >= tgtStartPos, < tgtSent.size())
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  @Override
  public int[] extractNgram(int pos, Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
    /* we don't use srcSent, alignment, srcStartPos */
    
    int tgtLen = tgtSent.size();
    assert(pos>=tgtStartPos && pos<tgtLen);
    
    int id;
    int[] ngram = new int[order]; // will be stored in normal order (cf. KenLM stores in reverse order)
    
    // extract tgt subsequence
    int i = 0;
    int tgtSeqStart = pos - order + 1;
    for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
      if(tgtPos<0) { id = startId; } // start
      else { id = toKenLMId(tgtSent.get(tgtPos)); } // within range 
      ngram[i++] = id;
    }
    
    return ngram;
  }

  @Override
  public int[] toId(Sequence<IString> sequence) {
    int[] ids = new int[sequence.size()];
    int i = 0;
    for(IString istring : sequence){
      ids[i++] = toKenLMId(istring);
    }
    return ids;
  }

  @Override
  public Sequence<IString> toIString(int[] kenlmIndices) {
    int[] istringIndices = new int[kenlmIndices.length];
    for (int i = 0; i < istringIndices.length; i++) {
      istringIndices[i] = kenlmIdToIstringId.get(kenlmIndices[i]);
    }
    
    return IString.getIStringSequence(istringIndices);
  }

  @Override
  public int getTgtOrder() {
    return order;
  }

}
