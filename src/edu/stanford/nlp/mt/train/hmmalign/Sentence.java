package edu.stanford.nlp.mt.train.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class Sentence {
  private WordEx[] words;

  public Sentence(WordEx[] wrds) {
    this.words = wrds;
  }

  public Sentence() {

  }

  public WordEx getWord(int index) {
    return words[index];
  }

  public int getLength() {
    return words.length;
  }

  public void print() {

    for (int i = 0; i < words.length; i++) {
      words[i].print();
    }
    System.out.println();
  }

}
