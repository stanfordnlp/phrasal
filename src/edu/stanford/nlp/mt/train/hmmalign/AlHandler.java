package edu.stanford.nlp.mt.train.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j) and incCount(i,j,val);
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class AlHandler {
  SentencePair sentPair;
  int l, m;
  double uniform;


  public AlHandler() {
  }


  public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
  }


  public void init() {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;
    uniform = 1 / (double) (l + 1);

  }


  /*
   * get the probability p choose i for j
   */
  public double getProb(int i, int j) {
    return uniform;

  }


  public double getProb(int i, int j, int[] al) {
    return uniform;
  }


  public void incCount(int i, int j, int[] al, double val) {
  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  public void incCount(int i, int j, double val) {
    //nothing
  }


}
