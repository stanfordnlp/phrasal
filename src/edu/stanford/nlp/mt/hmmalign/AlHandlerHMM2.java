package edu.stanford.nlp.mt.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * this is trigram HMM
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class AlHandlerHMM2 extends AlHandler {
  ATableHMM2 a;


  public AlHandlerHMM2() {
  }

  public AlHandlerHMM2(ATable a) {
    this.a = (ATableHMM2) a;
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


  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j, int[] alignment) {

    if (j == 1) {
      return a.getProb(i, 0, 0, l);
    }
    return a.getProb(i, alignment[j - 1], alignment[j - 2], l);

  }


  /**
   * increment the count for c(i|i_p,i_pp) by val
   */

  @Override
	public void incCount(int i, int j, int[] alignment, double val) {

    int i_p, i_pp;

    if (val == 0) {
      return;
    }

    if (j == 1) {
      i_p = 0;
      i_pp = 0;
    } else {
      i_p = alignment[j - 1];
      i_pp = alignment[j - 2];
    }
    //System.out.println("Incrementing count for "+i+" "+i_p+" "+i_pp+" with "+val+" length is "+l+" j is "+j);

    if (j == 1) {
      a.incCount(i, 0, 0, l, val);
      return;
    }
    a.incCount(i, i_p, i_pp, l, val);
    //nothing
  }


}
