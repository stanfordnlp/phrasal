package edu.stanford.nlp.mt.train.hmmalign;

/**
 * getProb(i,j) = p(fj|ei) if i not eq 0 and else p(fj|fj-1)
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class TPHandlerNULL extends TPHandler {

  //by default if there are eTags or fTags they will be ignored
  private ProbCountHolder[] cache; // cache for every p(fj| null,fj+1)
  double lambda = .2;
  TPHandler subHandler;
  private TTable fTable;
  int subkind;

  public TPHandlerNULL(TTable tTable, TTable fTable, int subkind) {//subkind 0 is simple 1 is eTags 2 is E&F tags
    super(tTable);
    this.fTable = fTable;
    this.subkind = subkind;

    if (subkind == 0) {
      subHandler = new TPHandler(tTable);
    }
    if (subkind == 1) {
      subHandler = new TPHandlerEtags(tTable);
    }

    if (subkind == 2) {
      subHandler = new TPHandlerFE1(tTable);

    }

    //System.out.println("values "+this.PROB_SMOOTH+" "+this.unifunknown);


  }


  @Override
	public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
    subHandler.setPair(sent);
  }


  /**
   * this init is different since we need to cache the tag probs as well
   */

  @Override
	public void init() {
    WordEx fWord;

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;
    cache = new ProbCountHolder[m + 1];
    //put first all probabilities in the cache
    for (int j = 1; j <= m; j++) {
      fWord = sentPair.f.getWord(j);
      tmpPair.setTarget(fWord.getWordId());
      if (j == m) {
        tmpPair.setSource(SentenceHandler.sTableF.getEos().getIndex());
      } else {
        tmpPair.setSource(sentPair.f.getWord(j + 1).getWordId());
      }
      cache[j] = fTable.get(tmpPair);

    }//j

  }


  /*
   * get the probability p(fj|ei)
   */
  @Override
	public double getProb(int i, int j) {

    double prob1, prob2, prob;

    prob1 = 0;

    prob2 = subHandler.getProb(i, j);
    //System.out.println(" prob2 is "+prob2);

    if (((i > l) || (i == 0)) && (i < 2l + 1)) {

      if (cache[j] == null) {

        prob1 = PROB_SMOOTH;//(sentPair.getSource().getWord(i).getCount()==0?PROB_SMOOTH:PROB_SMOOTH);

      } else {
        prob1 = cache[j].getProb();
        if (prob1 < PROB_SMOOTH) {
          prob1 = PROB_SMOOTH;
        }
      }


      if (subkind == 2) {
        prob1 *= (1 / (double) 50);
      }

      prob = lambda * prob1 + (1 - lambda) * prob2;

    } else {
      prob = prob2;
    }
    if ((i > l) || (i == 0)) {
      ;//System.out.println(" returning "+prob+" from "+prob1+" "+prob2+" for "+i+" "+j);
    }

    return prob;
  }


  /**
   * Increment the count for c(fj|ei)
   */

  @Override
	public void incCount(int i, int j, double val) {

    if (val == 0) {
      return;
    }

    if ((i == 0) || (i > l)) {
      if (cache[j] == null) {
        tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
        if (j == m) {
          tmpPair.setSource(SentenceHandler.sTableF.getEos().getIndex());
        } else {
          tmpPair.setSource(sentPair.f.getWord(j + 1).getWordId());
        }

        fTable.incCount(tmpPair, val, true);
      }//cache is null
      else {
        cache[j].incCount(val);

      }
    }

    subHandler.incCount(i, j, val);

  }


}
