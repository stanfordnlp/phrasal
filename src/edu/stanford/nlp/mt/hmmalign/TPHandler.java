package mt.hmmalign;

/**
 * Writing separate classes to handle the translation probabilities
 * with or without tags; these classes take care of the caching and reduce the
 * mess in the em_loops in the models. Trying to obviate the need for too many different
 * em_loop methods
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class TPHandler {
  SentencePair sentPair;
  TTable tTable;
  //by default if there are eTags or fTags they will be ignored
  protected ProbCountHolder[][] cache;
  int l, m;
  double PROB_SMOOTH = TTable.PROB_SMOOTH;
  double unifunknown = 1 / (double) SentenceHandler.sTableF.getMaxSimpleIds();
  protected IntPair tmpPair;

  public TPHandler() {
  }

  public TPHandler(TTable tTable) {
    this.tTable = tTable;
    tmpPair = new IntPair();

  }


  public void setPair(SentencePair sent) {
    sentPair = sent;
    init();
  }

  public void init() {
    Word fWord, eWord;

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    cache = new ProbCountHolder[l + 1][m + 1];
    //put first all probabilities in the cache
    for (int j = 1; j <= m; j++) {
      fWord = sentPair.f.getWord(j);
      tmpPair.setTarget(fWord.getWordId());
      for (int i = 0; i <= l; i++) {
        eWord = sentPair.e.getWord(i);
        tmpPair.setSource(eWord.getWordId());
        cache[i][j] = tTable.get(tmpPair);
      }
    }


  }


  /*
   * get the probability p(fj|ei)
   */
  public double getProb(int i, int j) {

    double prob;

    //take care of the case where i might be greater than l

    if ((i > l) && (i < 2 * l + 1)) {
      i = 0;
    }

    if (cache[i][j] == null) {

      prob = (sentPair.getSource().getWord(i).getCount() == 0 ? PROB_SMOOTH : PROB_SMOOTH);

    } else {
      prob = cache[i][j].getProb();
      if (prob < PROB_SMOOTH) {
        prob = PROB_SMOOTH;
      }
    }


    return prob;
  }


  /*
  * get the probability p(fj|ei)
  */
  public double getProb(int i, int j, int[] alignment) {

    double prob;

    //take care of the case where i might be greater than l

    if ((i > l) && (i < 2 * l + 1)) {
      i = 0;
    }

    if (cache[i][j] == null) {

      prob = (sentPair.getSource().getWord(i).getCount() == 0 ? PROB_SMOOTH : PROB_SMOOTH);

    } else {
      prob = cache[i][j].getProb();
      if (prob < PROB_SMOOTH) {
        prob = PROB_SMOOTH;
      }
    }


    return prob;
  }


  /**
   * Increment the count for c(fj|ei)
   */

  public void incCount(int i, int j, double val) {

    if (val == 0) {
      return;
    }

    if ((i > l) && (i < 2 * l + 1)) {
      i = 0;
    }

    if (cache[i][j] == null) {
      tmpPair.setSource(sentPair.e.getWord(i).getWordId());
      tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
      tTable.incCount(tmpPair, val, true);
    } else {
      cache[i][j].incCount(val);
    }


  }


}
