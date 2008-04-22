package mt.hmmalign;

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

  public void set(int num, int val) {
    if (num > 0) {
      target = val;
    } else {
      source = val;
    }
  }

  public int get(int num) {
    if (num > 0) {
      return target;
    } else {
      return source;
    }
  }

  public int hashCode() {
    return (65536 * source) ^ target;
  }

  public boolean equals(Object o) {
    IntPair iP = (IntPair) o;
    return ((iP.source == source) && (iP.target == target));

  }

  public void print() {
    System.out.println(target + "|" + source);
  }


  public String toString() {

    return source + " " + target;
  }


  public String toNameStringE() {

    return SentenceHandler.sTableE.getName(source) + " " + SentenceHandler.sTableE.getName(target);

  }


  public String toNameStringF() {

    return SentenceHandler.sTableF.getName(source) + " " + SentenceHandler.sTableF.getName(target);

  }


  public IntTuple getCopy() {
    IntPair nT = new IntPair(source, target);
    return nT;
  }


}
