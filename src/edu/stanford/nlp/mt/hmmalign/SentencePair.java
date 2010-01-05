package edu.stanford.nlp.mt.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class SentencePair {
  private int count;
  Sentence e;
  Sentence f;

  public SentencePair() {
  }

  public SentencePair(int count, Sentence e, Sentence f) {
    this.count = count;
    this.e = e;
    this.f = f;

  }

  public int getCount() {
    return count;
  }

  public Sentence getSource() {
    return e;
  }

  public Sentence getTarget() {
    return f;
  }


  public void print() {
    System.out.println("Count = " + count);
    e.print();
    f.print();


  }


}
