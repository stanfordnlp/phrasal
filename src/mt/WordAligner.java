package mt;

/**
 * An interface for getting out word alignment information from 
 * a WordAlignment and other possible information, such as IBMModel1, etc.
 * 
 * @author Pi-Chuan Chang
 */

public interface WordAligner {
  /**
   * @param sp Contains information of the foreign sentence, the English sentence, and the GIZA++ style alignment
   * @param foreignIdx the index of the foreign word that we want a specific alignment to
   * @return the index of the particular English word this foreign word (with index foreignIdx) is aligned to. If return -1, no good word alignment is produced
   */
  public int getAlignedEnglishIndex(WordAlignment sp, int foreignIdx);

}
