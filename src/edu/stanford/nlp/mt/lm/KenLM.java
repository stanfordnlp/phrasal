package edu.stanford.nlp.mt.lm;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * KenLM language model support via JNI.
 * 
 * @author daniel cer (danielcer@stanford.edu)
 * @author Spence Green
 * @author Kenneth Heafield
 *
 */
public class KenLM {

  private static final Logger logger = LogManager.getLogger(KenLM.class.getName());
  
  // Should match util::LoadMethod defined in kenlm/util/mmap.hh
  public static enum LoadMethod {
    LAZY,
    POPULATE_OR_LAZY,
    POPULATE_OR_READ,
    READ,
    PARALLEL_READ
  }

  public final static int DEFAULT_LOAD_METHOD = LoadMethod.POPULATE_OR_READ.ordinal();

  static {
    System.loadLibrary("PhrasalKenLM");
  }

  private final int order;
  private final long kenLMPtr;

  private final int bos, eos;

  // JNI methods
  private native long readKenLM(String filename, long nplm_cache_size, int loadMethod);
  private native long scoreNGramSeq(long kenLMPtr, int[] ngram, int startIndex);
  private native long scoreNGram(long kenLMPtr, int[] ngram);
  private native int getLMId(long kenLMPtr, String token);
  private native int getOrder(long kenLMPtr);

  /**
   * Constructor.
   * 
   * @param filename
   */
  public KenLM(String filename) {
    this(filename, (long)(1 << 20), DEFAULT_LOAD_METHOD);
  }

  /**
   * Constructor.
   * 
   * @param filename
   * @param loadMethod
   */
  public KenLM(String filename, LoadMethod loadMethod) {
    this(filename, (long)(1 << 20), loadMethod.ordinal());
  }
  
  /**
   * Constructor for multi-threaded queries.
   * 
   * @param filename
   * @param nplm_cache_size
   * @param loadMethod
   */
  public KenLM(String filename, long nplm_cache_size, int loadMethod) {
    logger.info("KenLM: Reading {}", filename);
    if (0 == (kenLMPtr = readKenLM(filename, nplm_cache_size, loadMethod))) {
      File f = new File(filename);
      if (!f.exists()) {
        new RuntimeException(String.format("Error loading %s - file not found", filename));
      } else {
        new RuntimeException(String.format("Error loading %s - file is likely corrupt or created with an incompatible version of kenlm", filename));
      } 
    }
    order = getOrder(kenLMPtr);
    bos = index("<s>");
    eos = index("</s>");
  }

  /**
   * Maps a String to a KenLM id.
   */
  public int index(String token) {
    return getLMId(kenLMPtr, token);
  }

  public int BeginSentence() { return bos; }
  public int EndSentence() { return eos; }

  public int order() {
    return order;
  }

  /**
   * Return the right state length in the high 32 bits and the score in the low
   * 32 bits.  Provide the words in reverse order, so words[0] is the word being
   * predicted.
   */
  public long marshalledScore(int words[]) {
    return scoreNGram(kenLMPtr, words);
  }

  public static float scoreFromMarshalled(long marshalled) {
    return Float.intBitsToFloat((int)(marshalled & 0xffffffff));
  }

  /**
   * Get the right state length (number of words to keep) from a marshalled
   * score.
   */
  public static int rightStateFromMarshalled(long marshalled) {
    return (int)(marshalled >> 32);
  }

  /**
   * Score words[0] given words[1..words.size - 1].  These should be in reverse
   * order.  If you need the right state length, use marshalledScore.
   */
  public float score(int words[]) {
    return scoreFromMarshalled(marshalledScore(words));
  }
  
  /**
   * Score a sequence of words.  Returns a marshalled long.  Use
   * scoreFromMarshalled and rightStateFromMarshalled to extract information.
   */
  public long scoreSeqMarshalled(int words[], int startIndex) {
    return scoreNGramSeq(kenLMPtr, words, startIndex);
  }

  /**
   * Just get the score of a sequence if you don't care about right state length
   */
  public float scoreSeq(int words[], int startIndex) {
    return scoreFromMarshalled(scoreSeqMarshalled(words, startIndex));
  }
}
