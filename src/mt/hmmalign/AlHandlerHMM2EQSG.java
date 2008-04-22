package mt.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM2EQSG extends AlHandler {

  StayGoTables sgTables;
  int mask;
  AlHandler subHandler;
  double PROB_EMPTY = .4;
  double lambda = .2;
  double empty;

  public AlHandlerHMM2EQSG() {
  }

  public AlHandlerHMM2EQSG(StayGoTables sg, int mask, ATableHMMHolder aHolder, ATable a) {
    this.sgTables = sg;
    this.mask = mask;
    if (mask > 0) {
      subHandler = new AlHandlerHMM2Tags(aHolder, mask, true);
    } else {
      subHandler = new AlHandlerHMM2EQ(a);
    }

  }


  public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
    subHandler.setPair(sent);
  }


  public void init() {

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;
    setEmpty();
  }


  /*
   * get the probability p choose i for j
   */
  public double getProb(int i, int j, int[] alignment) {
    int ireal = 0;
    double prob = 0;
    int jump = ATableHMM2EQ.MAX_FLDS;
    //System.out.println(" getting prob "+i+" "+j);

    if ((i > l) && (i < 2 * l + 1)) {
      return empty;
    }

    ireal = alignment[j - 1];
    if (alignment[j - 1] > l) {
      ireal = alignment[j - 1] - l;
    }
    if (j > 1) {
      jump = alignment[j - 2];
    }
    int index = sentPair.e.getWord(ireal).getWordId();

    if ((i <= l) && ((i == alignment[j - 1]) || (i == alignment[j - 1] - l))) {
      //stay
      prob = sgTables.getProbStay(index, jump);
    } else { //go
      prob = sgTables.getProbGo(index, jump) * subHandler.getProb(i, j, alignment);

    }
    //System.out.println(" Returning "+prob);


    return (1 - empty) * prob;

  }


  public void setEmpty() {

    empty = (PROB_EMPTY * (1 - lambda) + lambda * (1 / (double) (l + 2)));

  }



  /*
   * Increment the appropriate probabilities
   *
   */

  public void incCount(int i, int j, int[] alignment, double val) {
    if (val == 0) {
      return;
    }
    //System.out.println("Incrementing "+i+" "+alignment[j-1]+" with "+val);
    int jump = ATableHMM2EQ.MAX_FLDS;

    if ((i > l) && (i < 2 * l + 1)) {
      return;
    }

    int ireal = alignment[j - 1];
    if (alignment[j - 1] > l) {
      ireal = alignment[j - 1] - l;
    }
    if (j > 1) {
      jump = alignment[j - 2];
    }
    int index = sentPair.e.getWord(ireal).getWordId();

    if ((i <= l) && ((i == alignment[j - 1]) || (i == alignment[j - 1] - l))) {
      //stay
      sgTables.incCountStay(index, val, jump);

    } else {
      //go
      sgTables.incCountGo(index, val, jump);
      subHandler.incCount(i, j, alignment, val);
    }

  }


}
