package edu.stanford.nlp.mt.lm;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

public abstract class AbstractNNLM implements NNLM {
  protected String name;
  protected int order;

  /**
   * Extract ngrams that we want to score after adding a phrase pair. 
   * 
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  @Override
  public int[][] extractNgrams(Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
    int tgtLen = tgtSent.size();
    int[][] ngrams = new int[tgtLen-tgtStartPos][];
    
    int i = 0;
    for (int pos = tgtStartPos; pos < tgtLen; pos++) {
      ngrams[i++] = extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    }
    
    return ngrams;
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
  public abstract int[] extractNgram(int pos, Sequence<IString> srcSent,
      Sequence<IString> tgtSent, PhraseAlignment alignment, int srcStartPos,
      int tgtStartPos);

  @Override
  public abstract double scoreNgram(int[] ngram);

  @Override
  public abstract double[] scoreNgrams(int[][] ngrams);

  @Override
  public abstract int[] toId(Sequence<IString> sequence);

  @Override
  public abstract Sequence<IString> toIString(int[] nnlmIds);
  @Override
  public abstract int getTgtOrder(); 
}
