package mt.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * this is trigram HMM
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class AlHandlerHMM2EQ extends AlHandler {
  ATableHMM2EQ a;


  public AlHandlerHMM2EQ() {
  }

  public AlHandlerHMM2EQ(ATable a) {
    this.a = (ATableHMM2EQ) a;
  }


  public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
  }


  public void init() {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;


  }


  /*
   * get the probability p choose i for j
   */
  public double getProb(int i, int j, int[] alignment) {

    if (j == 1) {
      return a.getProb(i, 0, ATableHMM2EQ.MAX_FLDS, l);
    }
    return a.getProb(i, alignment[j - 1], alignment[j - 2], l);

  }


  /**
   * increment the count for c(i|i_p,i_pp) by val
   */

  public void incCount(int i, int j, int[] alignment, double val) {

    int i_p, i_pp;

    if (val == 0) {
      return;
    }

    if (j == 1) {
      i_p = 0;
      i_pp = ATableHMM2EQ.MAX_FLDS;
    } else {
      i_p = alignment[j - 1];
      i_pp = alignment[j - 2];
    }
    //System.out.println("Incrementing count for "+i+" "+i_p+" "+i_pp+" with "+val+" length is "+l+" j is "+j);

    a.incCount(i, i_p, i_pp, l, val);
    //nothing
  }


}
