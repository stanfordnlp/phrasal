package edu.stanford.nlp.mt.train.hmmalign;

/*
* Holds a pair of a French and an English Word
*@author Kristina Toutanova (kristina@cs.stanford.edu)
*/

public class WordPair {
  private Word source;
  private Word target;
  private int hashcode;

  public WordPair(Word source, Word target) {
    this.source = source;
    this.target = target;
    init();
  }


  public WordPair() {
  }


  private void init() {
    hashcode = source.hashCode() ^ target.hashCode();

  }

  public Word getSource() {
    return source;
  }

  public Word getTarget() {
    return target;
  }

  @Override
	public int hashCode() {
    return hashcode;
  }


  public boolean equals(WordPair wP) {
    return (source.equals(wP.getSource()) && target.equals(wP.getTarget()));

  }


} 
