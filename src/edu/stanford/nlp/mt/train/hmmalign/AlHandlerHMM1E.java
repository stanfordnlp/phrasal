package edu.stanford.nlp.mt.train.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j) and incCount(i,j,val);
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM1E extends AlHandlerHMM1 {

  ATableHMMHolder aHolder;
  ATableHMM[] tables;


  public AlHandlerHMM1E(ATableHMMHolder a) {
    this.aHolder = a;
  }


  @Override
  public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
  }


  @Override
  public void init() {
    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;
    tables = new ATableHMM[2 * l + 1];
    IntPair iP = new IntPair();
    for (int i_p = 0; i_p <= 2 * l; i_p++) {
      int indexip = i_p;
      if (indexip > l) {
        indexip -= l;
      }
      iP.setTarget(sentPair.e.getWord(indexip).getTagId());
      tables[i_p] = (ATableHMM) aHolder.get(iP);
    }
  }


  /*
   * get the probability p choose i for j
   */
  @Override
  public double getProb(int i, int j, int[] alignment) {
    ATableHMM a = tables[alignment[j - 1]];
    return a.getProb(i, alignment[j - 1], l);

  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
  public void incCount(int i, int j, int[] alignment, double val) {
    ATableHMM a = tables[alignment[j - 1]];
    a.incCount(i, alignment[j - 1], l, val);
    //nothing
  }


}
