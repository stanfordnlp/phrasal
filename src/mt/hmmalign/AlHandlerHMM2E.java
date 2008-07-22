package mt.hmmalign;

/**
 * This serves to handle the alignment probabilities
 * this is trigram HMM . The tags of the previous two english
 * words are used as well
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class AlHandlerHMM2E extends AlHandlerHMM2 {
  ATableHMMHolder aHolder;
  ATableHMM2[][] tables;


  public AlHandlerHMM2E(ATableHMMHolder aHolder) {
    this.aHolder = aHolder;
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
    tables = new ATableHMM2[2 * l + 1][2 * l + 1];
    IntPair iP = new IntPair();
    for (int i_p = 0; i_p <= 2 * l; i_p++) {
      int bound = 2 * l;
      int start = 0;
      int inc = 1;
      if (i_p == 0) {
        bound = 0;
      }
      if (i_p > l) {
        bound = i_p;
        start = i_p - l;
        inc = l;
      }
      int indexip = i_p;
      if (indexip > l) {
        indexip -= l;
      }
      iP.setSource(sentPair.e.getWord(indexip).getTagId());
      for (int i_pp = start; i_pp <= bound; i_pp += inc) {
        int indexipp = i_pp;
        if (indexipp > l) {
          indexipp -= l;
        }
        iP.setTarget(sentPair.e.getWord(indexipp).getTagId());
        tables[i_p][i_pp] = (ATableHMM2) aHolder.get(iP);

      }


    }

  }


  /*
   * get the probability p choose i for j
   */
  @Override
	public double getProb(int i, int j, int[] alignment) {


    if (j == 1) {
      a = tables[0][0];
      return a.getProb(i, 0, 0, l);
    }

    if ((alignment[j - 1] == 0) && (alignment[j - 2] > 0)) {
      return 0;
    }
    if ((alignment[j - 1] > l) && (alignment[j - 2] != alignment[j - 1]) && (alignment[j - 2] != alignment[j - 1] - l)) {
      return 0;
    }

    a = tables[alignment[j - 1]][alignment[j - 2]];
    if (a == null) {

      System.out.println("The table for i_p " + alignment[j - 1] + " i_pp " + alignment[j - 2] + " length " + l + " is null");
      return 0;
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
      a = tables[0][0];
    } else {
      i_p = alignment[j - 1];
      i_pp = alignment[j - 2];
      a = tables[i_p][i_pp];
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
