package edu.stanford.nlp.mt.train.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class TPHandlerEtags extends TPHandler {

  // by default if there are eTags or fTags they will be ignored
  private ProbCountHolder[][] cache_tags;
  private double[] lambdas; // these will be interpolation weights

  public TPHandlerEtags(TTable tTable) {
    super(tTable);
    System.out.println("values " + this.PROB_SMOOTH + " " + this.unifunknown);

  }

  /**
   * this init is different since we need to cache the tag probs as well
   */

  @Override
  public void init() {
    WordEx fWord, eWord;

    l = sentPair.e.getLength() - 1;
    m = sentPair.f.getLength() - 1;

    lambdas = new double[l + 1];
    cache = new ProbCountHolder[l + 1][m + 1];
    cache_tags = new ProbCountHolder[l + 1][m + 1];
    // put first all probabilities in the cache
    for (int j = 1; j <= m; j++) {
      fWord = sentPair.f.getWord(j);
      tmpPair.setTarget(fWord.getWordId());
      for (int i = 0; i <= l; i++) {
        eWord = sentPair.e.getWord(i);
        tmpPair.setSource(eWord.getIndex());
        cache[i][j] = tTable.get(tmpPair);
        tmpPair.setSource(eWord.getTagId());
        cache_tags[i][j] = tTable.get(tmpPair);
        lambdas[i] = .9;
      }// i
    }// j

  }

  /*
   * get the probability p(fj|ei)
   */
  @Override
  public double getProb(int i, int j) {

    double prob1, prob2, prob;
    if (cache[i][j] == null) {

      prob1 = (sentPair.getSource().getWord(i).getCount() == 0 ? PROB_SMOOTH
          : PROB_SMOOTH);

    } else {
      prob1 = cache[i][j].getProb();
      if (prob1 < PROB_SMOOTH) {
        prob1 = PROB_SMOOTH;
      }
    }

    if (cache_tags[i][j] == null) {

      prob2 = (SentenceHandler.sTableE.getEntry(
          sentPair.e.getWord(i).getTagId()).getCount() == 0 ? PROB_SMOOTH
          : PROB_SMOOTH);

    } else {
      prob2 = cache_tags[i][j].getProb();
      if (prob2 < PROB_SMOOTH) {
        prob2 = PROB_SMOOTH;
      }
    }

    prob = lambdas[i] * prob1 + (1 - lambdas[i]) * prob2;
    // System.out.println(" returning "+prob+" from "+prob1+" "+prob2+" for "+i+" "+j);

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

    if (cache[i][j] == null) {
      tmpPair.setSource(sentPair.e.getWord(i).getIndex());
      tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
      tTable.incCount(tmpPair, lambdas[i] * val, true);
    } else {
      cache[i][j].incCount(lambdas[i] * val);
    }

    if (cache_tags[i][j] == null) {

      tmpPair.setSource(sentPair.e.getWord(i).getTagId());
      tmpPair.setTarget(sentPair.f.getWord(j).getWordId());
      tTable.incCount(tmpPair, (1 - lambdas[i]) * val, true);
    } else {
      cache_tags[i][j].incCount((1 - lambdas[i]) * val);
    }

  }

}
