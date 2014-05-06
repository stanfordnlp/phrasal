/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Interface for Neural Network Language Model (NNLM).
 * 
 * @author Thang Luong
 *
 */
public interface NNLM {
  /**
   * Score a single ngram.
   * 
   * @param ngramIds: normal order ids
   * @return
   */
  public double scoreNgram(int[] ngrams);
  
  /**
   * Score multiple ngrams.
   * 
   * @param ngrams: ngrams[i] is the i-th ngram.
   * @return
   */
  public double[] scoreNgrams(int[][] ngrams);
  
  /**
   * Extract ngrams that we want to score after adding a phrase pair. 
   * 
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return array of ngramIds, each of which consists of NNNLM ids. The number of rows is equal to the number of ngrams.
   */
  public int[][] extractNgrams(Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos);
  
  /**
   * Convert a sequence of IString into an array of indices.
   * 
   * @param sequence
   * @return
   */
  
  public int[] toId(Sequence<IString> sequence);
  
  /**
   * Convert an array of indices into a sequence of IString.
   * 
   * @param sequence
   * @return
   */
  public Sequence<IString> toIString(int[] ngramIds);
  
  
  /**
   * Name for this NNLM.
   *
   * @return
   */
  public String getName();
  
  /**
   * Order of this NNLM.
   * 
   * @return
   */
  public int order();
}
