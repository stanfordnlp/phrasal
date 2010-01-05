package mt.hmmalign;

/**
 * This handles the translation probability
 * for the model p(fj,tfj|aj,e,te)=p(tfj|teaj)p(fj|eaj,[teaj,tfj])
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class TPHandlerFE1 extends TPHandler {

  //by default if there are eTags or fTags they will be ignored

  TPHandler subHandler; //this one will handle p(fj|eaj,[teaj,tfj])
  byte kindsub = 0; //simple 1 is with eTags , 2 is with ftags, and 3 is with both
  float PROB_EMPTY = (float) .4;
  double correctFactor = 1;
  boolean specialEmpty = true;
  float uniftags = 0;

  public TPHandlerFE1(TTable tTable) {
    this.tTable = tTable;
    tmpPair = new IntPair();
    if (GlobalParams.useETagsT) {
      kindsub = 0;
    }
    if (kindsub == 0) {
      subHandler = new TPHandler(tTable);
    }
    if (kindsub == 1) {
      subHandler = new TPHandlerEtags(tTable);
    }
    PROB_SMOOTH = 1e-7;
    unifunknown = 1e-2;
    correctFactor = 1 / (1 - subHandler.PROB_SMOOTH * SentenceHandler.sTableF.getNumWords());
    uniftags = 1 / (float) SentenceHandler.sTableF.getNumTags();


  }


  @Override
	public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
    subHandler.setPair(sent);
  }

  @Override
	public void init() {
    Word fWord, eWord;

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    cache = new ProbCountHolder[l + 1][m + 1];
    //put first all probabilities in the cache
    for (int j = 1; j <= m; j++) {
      fWord = sentPair.f.getWord(j);
      tmpPair.setTarget(fWord.getTagId());
      for (int i = 0; i <= l; i++) {
        eWord = sentPair.e.getWord(i);
        tmpPair.setSource(eWord.getTagId());
        cache[i][j] = tTable.get(tmpPair);
      }
    }


  }


  /*
   * get the probability p(fj|ei)
   */
  @Override
	public double getProb(int i, int j) {

    double prob;

    if ((i > l) && (i < 2 * l + 1)) {
      i = 0;
    }

    if (cache[i][j] == null) {

      prob = (unifunknown);

    } else {
      prob = cache[i][j].getProb();
      if (prob < PROB_SMOOTH) {
        prob = PROB_SMOOTH;
      }
    }


    //prob=prob*correctFactor;

    if (specialEmpty) {
      if (i == 0) {
        prob = uniftags;
      } else {
        prob = (.01 * prob + .99 * uniftags);
      }

    } else {
      prob = .1 * prob + .9 * (1 / (float) 50);

    }


    if (!(prob == (PROB_SMOOTH * correctFactor))) {
      ;//System.out.println("prob "+sentPair.e.getWord(i).getTagId()+" "+sentPair.f.getWord(j).getTagId()+" "+prob);
    }

    //double prob_last=prob*subHandler.getProb(i,j);
    //System.out.println(" tag handler returning "+prob_last);
    return prob * subHandler.getProb(i, j);
  }


  /**
   * Increment the count for c(fj|ei)
   */

  @Override
	public void incCount(int i, int j, double val) {

    if (val == 0) {
      return;
    }

    if ((i > l) && (i < 2 * l + 1)) {
      i = 0;
    }

    //if((i==0)&&(specialEmpty)){}else{
    if (cache[i][j] == null) {
      tmpPair.setSource(sentPair.e.getWord(i).getTagId());
      tmpPair.setTarget(sentPair.f.getWord(j).getTagId());
      tTable.incCount(tmpPair, val, true);
    } else {
      cache[i][j].incCount(val);
    }
    //}
    subHandler.incCount(i, j, val);

  }


}
