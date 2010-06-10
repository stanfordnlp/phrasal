package edu.stanford.nlp.mt.train.hmmalign;

/**
 * The purpose of this class is to handle the choose/ not choose probabilities
 * for english words that I am implementing
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */


public class CHandler extends AlHandler {
  PTable pTable; // this is the table that holds the choose/ not choose probs
  //by default if there are eTags or fTags they will be ignored
  protected ProbCountHolder[] cache_choose;
  protected ProbCountHolder[] cache_notchoose;
  boolean eTags;
  double total;
  static double PROB_SMOOTH = TTable.PROB_SMOOTH;
  double lambda = 0.001;
  AlHandler subHandler;


  public CHandler() {
    subHandler = new AlHandler();
  }

  public CHandler(PTable pTable, boolean eTags) {
    this.pTable = pTable;
    this.eTags = eTags;
    subHandler = new AlHandler();

  }


  @Override
	public void setPair(SentencePair sent) {
    this.sentPair = sent;
    init();
    subHandler.setPair(sent);
  }


  @Override
	public void init() {
    int id;
    total = 0;
    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    cache_choose = new ProbCountHolder[l + 1];
    //cache_notchoose=new ProbCountHolder[l+1];
    //put first all probabilities in the cache

    total = 0;

    for (int i = 0; i <= l; i++) {
      if (eTags) {
        id = sentPair.e.getWord(i).getIndex();
      } else {
        id = sentPair.e.getWord(i).getWordId();
      }

      cache_choose[i] = pTable.getEntryChoose(id);

      double prob = cache_choose[i].getProb();
      if (prob == 0) {
        prob = PROB_SMOOTH;
      }
      //cache_notchoose[i]=pTable.getEntryNotChoose(id);
      total += prob;

    }//i


  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j) {

    double prob;
    prob = (cache_choose[i].getProb());
    if (prob == 0) {
      prob = PROB_SMOOTH;
    }
    prob /= total;
    //System.out.println("from here "+prob+" from super "+super.getProb(i,j));
    prob = lambda * prob + (1 - lambda) * subHandler.getProb(i, j);
    //System.out.println("length "+l+" prob "+prob);
    return prob;

  }


  /**
   * Increment the count for c(choose|ei) by val and also increment the probability for not choose
   * by 1-val
   */

  @Override
	public void incCount(int i, int j, double val) {


    cache_choose[i].incCount(val);
    subHandler.incCount(i, j, val);
    //cache_notchoose[i].incCount(sentPair.getCount()-val);


  }


}
