package edu.stanford.nlp.mt.hmmalign;

/*
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j) and incCount(i,j,val);
 * p(aj|aj-1,tfj)
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM1F extends AlHandlerHMM1 {
  ATableHMMHolder aHolder;
  ATableHMM tables[];


  public AlHandlerHMM1F(ATableHMMHolder a) {
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
    tables = new ATableHMM[m + 1];
    IntPair iP = new IntPair();
    for (int j = 1; j <= m; j++) {
      iP.setTarget(sentPair.f.getWord(j).getTagId());
      tables[j] = (ATableHMM) aHolder.get(iP);
    }
  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j, int[] alignment) {
    ATableHMM a = tables[j];
    return a.getProb(i, alignment[j - 1], l);

  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
	public void incCount(int i, int j, int[] alignment, double val) {
    ATableHMM a = tables[j];
    a.incCount(i, alignment[j - 1], l, val);
    //nothing
  }


}
