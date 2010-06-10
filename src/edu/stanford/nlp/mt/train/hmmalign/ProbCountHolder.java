package edu.stanford.nlp.mt.train.hmmalign;


/* Stores a probability , which is a double, and a count, which is
 * double as well
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class ProbCountHolder {
  private double prob;
  private double count;

  public ProbCountHolder() {
  }

  public ProbCountHolder(double prob, double count) {
    this.prob = prob;
    this.count = count;
  }


  public void swap() {
    double tmp = prob;
    prob = count;
    count = tmp;

  }

  public double getProb() {
    return prob;
  }

  public double getCount() {
    return count;
  }

  public void setProb(double prob) {
    this.prob = prob;
  }

  public void setCount(double count) {
    this.count = count;
  }

  public void incCount(double cnt) {
    count += cnt;
  }

} 
