package edu.stanford.nlp.mt.train.hmmalign;

/*
 * Holds a pair of a French and an English Word
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class WordExPair {
  private WordEx source;
  private WordEx target;
  private int hashcode;

  public WordExPair(WordEx source, WordEx target) {
    this.source = source;
    this.target = target;
    init();
  }

  public WordExPair() {
  }

  public void init() {
    hashcode = source.hashCode() ^ target.hashCode();

  }

  public WordEx getSource() {
    return source;
  }

  public void setSource(WordEx src) {
    this.source = src;
  }

  public WordEx getTarget() {
    return target;
  }

  public void setTarget(WordEx trgt) {
    this.target = trgt;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public boolean equals(Object wP) {

    if (!(wP instanceof WordExPair)) {
      return false;
    } else {
      WordExPair wP1 = (WordExPair) wP;
      return ((source.getIndex() == wP1.getSource().getIndex()) && (target
          .getIndex() == wP1.getTarget().getIndex()));
    }
  }

  public void print() {

    System.out.println("Source ");
    source.print();
    System.out.println("Target ");
    target.print();

  }

}
