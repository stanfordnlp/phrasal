/**
 * 
 */
package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.base.IString;
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
