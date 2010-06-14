package edu.stanford.nlp.mt.train.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * the basic functionality is getProb(i,j) and incCount(i,j,val);
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class AlHandlerHMM1SG extends AlHandlerHMM1 {
  StayGoTable sgTable;
  int mask;
  AlHandlerHMM1 subHandler;
  ProbCountHolder[] cacheS;
  ProbCountHolder[] cacheG;
  double PROB_EMPTY = .4;
  double lambda = .2;
  double empty;

  @SuppressWarnings("unused")
  public AlHandlerHMM1SG() {
  }

  public AlHandlerHMM1SG(StayGoTable sg, int mask, ATableHMMHolder aHolder, ATable a) {
    this.sgTable = sg;
    this.mask = mask;
    if (mask > 0) {
      subHandler = new AlHandlerHMM1Tags(aHolder, mask);
    } else {
      subHandler = new AlHandlerHMM1(a);
    }

  }


  @Override
	public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
    subHandler.setPair(sent);
  }


  @Override
	public void init() {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;
    cacheS = new ProbCountHolder[l + 1]; // from 0 to l;
    cacheG = new ProbCountHolder[l + 1];
    for (int i = 0; i <= l; i++) {

      cacheS[i] = sgTable.getEntryStay(sentPair.e.getWord(i).getWordId());
      cacheG[i] = sgTable.getEntryGo(sentPair.e.getWord(i).getWordId());

    }
    setEmpty();
  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j, int[] alignment) {
    double prob; // = 0;
    //System.out.println(" getting prob "+i+" "+j);

    if ((i <= l) && ((i == alignment[j - 1]) || (i == alignment[j - 1] - l))) {
      //stay
      prob = cacheS[i].getProb();
    } else { //go
      prob = cacheG[alignment[j - 1] > l ? alignment[j - 1] - l : alignment[j - 1]].getProb() * subHandler.getProb(i, j, alignment);

    }
    //System.out.println(" Returning "+prob);

    if ((i > l) && (i < 2 * l + 1)) {

      return (empty);
    } else {
      return (1 - empty) * prob;

    }


  }


  public void setEmpty() {

    empty = (PROB_EMPTY * (1 - lambda) + lambda * (1 / (double) (l + 2)));

  }



  /*
   * Increment the appropriate probabilities
   *
   */

  @Override
	public void incCount(int i, int j, int[] alignment, double val) {
    if (val == 0) {
      return;
    }
    //System.out.println("Incrementing "+i+" "+alignment[j-1]+" with "+val);


    if ((i > l) && (i < 2 * l + 1)) {
      return;
    }

    if ((i <= l) && ((i == alignment[j - 1]) || (i == alignment[j - 1] - l))) {
      //stay
      cacheS[i].incCount(val);

    } else {
      //go
      int i_p = alignment[j - 1];
      cacheG[i_p > l ? i_p - l : i_p].incCount(val);
      subHandler.incCount(i, j, alignment, val);
    }

  }


}
