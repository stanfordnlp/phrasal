package edu.stanford.nlp.mt.train;

/**
 * Filter for phrase extraction: a given phrase pair (F,E) is extracted and
 * weighed only if F is listed is accepted by the filter.
 * 
 * @author Michel Galley
 */
public interface SourceFilter {

  /**
   * Is filter enabled?
   */
  public boolean isEnabled();

  /**
   * Determines whether or not phrase alTemp should be extracted and weighed.
   */
  public boolean allows(AlignmentTemplate alTemp);

  /**
   * All source-language phrases found in sourceLanguageCorpus are added to the
   * filter.
   * 
   * @param sourceLanguageCorpus
   */
  public void filterAgainstCorpus(String sourceLanguageCorpus);

  /**
   * Number of source phrases in the filter.
   */
  public int size();

  /**
   * Only filter extraction against phrases whose ids range between startId and
   * endId.
   */
  public void setRange(int startId, int endId);

  /**
   * Once a SourceFilter instance is locked, no more phrases can be added to the
   * filter. Note: Locking the filter speeds up multi-threaded phrase extraction
   * with some instantiations of SourceFilter (e.g., PhrasalSourceFilter and
   * DTUSourceFilter), since these instantiations can operate without
   * synchronization.
   */
  public void lock();
}
