package edu.stanford.nlp.mt.train.hmmalign;

/**
 * Title:        Statistical MT tool
 * Description:  Your description
 * Copyright:    Copyright (c) 1999
 * Company:      Stanford University
 *
 * @author Kristina Toutanova
 * @version 1.0
 */

/* In the hope that for the translation table using these will yield
 * better performance
 */

public class IntPair extends IntTuple {
  private int source;
  private int target;


  public IntPair() {
//    numElements = 2;
  }

  public IntPair(int src, int trgt) {
    source = src;
    target = trgt;
//    numElements = 2;
    //init();

  }


  public int getSource() {
    return source;
  }

  public int getTarget() {
    return target;
  }

  public void setTarget(int trgt) {
    target = trgt;
  }

  public void setSource(int src) {
    source = src;
  }

  @Override
	public void set(int num, int val) {
    if (num > 0) {
      target = val;
    } else {
      source = val;
    }
  }

  @Override
	public int get(int num) {
    if (num > 0) {
      return target;
    } else {
      return source;
    }
  }

  @Override
	public int hashCode() {
    return (65536 * source) ^ target;
  }

  @Override
	public boolean equals(Object o) {
    IntPair iP = (IntPair) o;
    return ((iP.source == source) && (iP.target == target));

  }

  @Override
	public void print() {
    System.out.println(target + "|" + source);
  }


  @Override
	public String toString() {

    return source + " " + target;
  }


  @Override
	public String toNameStringE() {

    return SentenceHandler.sTableE.getName(source) + " " + SentenceHandler.sTableE.getName(target);

  }


  @Override
	public String toNameStringF() {

    return SentenceHandler.sTableF.getName(source) + " " + SentenceHandler.sTableF.getName(target);

  }


  @Override
	public IntTuple getCopy() {
    IntPair nT = new IntPair(source, target);
    return nT;
  }


}
