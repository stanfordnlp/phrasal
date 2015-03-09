package edu.stanford.nlp.mt.tm;

/**
 * Cache for decoder-local translation models.
 * 
 * @author Spence Green
 *
 */
public final class DecoderLocalTranslationModel {

  private DecoderLocalTranslationModel() {}
  

  // Thread local copies of translation model indices
  private static final ThreadLocal<PhraseGenerator> threadLocalCache =
      new ThreadLocal<PhraseGenerator>();

  /**
   * Set the thread-local index.
   * 
   * @param index
   */
  public static void set(PhraseGenerator translationModel) {
    threadLocalCache.set(translationModel);
  }

  /**
   * Get the thread-local index.
   * 
   * @return
   */
  public static PhraseGenerator get() {
    return threadLocalCache.get();
  }
}
