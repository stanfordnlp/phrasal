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
  @SuppressWarnings("rawtypes")
  private static final ThreadLocal<TranslationModel> threadLocalCache =
      new ThreadLocal<TranslationModel>();

  /**
   * Set the thread-local index.
   * 
   * @param index
   */
  @SuppressWarnings("rawtypes")
  public static void set(TranslationModel translationModel) {
    threadLocalCache.set(translationModel);
  }

  /**
   * Get the thread-local index.
   * 
   * @return
   */
  @SuppressWarnings("rawtypes")
  public static TranslationModel get() {
    return threadLocalCache.get();
  }
}
