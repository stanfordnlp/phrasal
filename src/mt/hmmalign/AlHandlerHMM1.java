package mt.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j) and incCount(i,j,val);
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class AlHandlerHMM1 extends AlHandler {
  ATable a;


  public AlHandlerHMM1() {
  }

  public AlHandlerHMM1(ATable a) {
    this.a = a;
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
    return a.getProb(i, alignment[j - 1], l);

  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
	public void incCount(int i, int j, int[] alignment, double val) {
    if (val == 0) {
      return;
    }
    a.incCount(i, alignment[j - 1], l, val);
    //nothing
  }


}
