package edu.stanford.nlp.mt.train.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class IntQuadruple extends IntTuple {
  private int source;
  private int middle;
  private int target;
  private int target2;

  public IntQuadruple() {
    // numElements = 4;

  }

  public IntQuadruple(int src, int mid, int trgt, int trgt2) {
    source = src;
    target = trgt;
    middle = mid;
    target2 = trgt2;
    // numElements = 4;
    // init();
  }

  @Override
  public IntTuple getCopy() {
    return new IntQuadruple(source, middle, target, target2);
  }

  @Override
  public void set(int num, int val) {
    switch (num) {
    case 0: {
      source = val;
    }
    case 1: {
      middle = val;
    }
    case 2: {
      target = val;
    }
    case 3: {
      target2 = val;
    }

    }

  }

  @Override
  public int get(int num) {
    if (num == 0) {
      return source;
    }
    if (num == 1) {
      return middle;
    }
    if (num == 2) {
      return target;
    }
    if (num == 3) {
      return target2;
    }
    return 0;
  }

  public int getSource() {
    return source;
  }

  public int getTarget() {
    return target;
  }

  public int getTagret2() {
    return target2;
  }

  public int getMiddle() {
    return middle;
  }

  public void setTarget(int trgt) {
    target = trgt;
  }

  public void setSource(int src) {
    source = src;
  }

  public void setMiddle(int mid) {
    middle = mid;
  }

  public void setTarget2(int trgt2) {
    target2 = trgt2;
  }

  @Override
  public int hashCode() {
    return (source << 20) ^ (middle << 10) ^ (target << 5) ^ (target2);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null)
      return false;
    if (!(o instanceof IntQuadruple))
      return false;
    IntQuadruple iP = (IntQuadruple) o;
    return ((iP.source == source) && (iP.middle == middle)
        && (iP.target == target) && (iP.target2 == target2));

  }

  @Override
  public void print() {
    System.out.println(target2 + "|" + target + "|" + middle + "|" + source);
  }

  @Override
  public String toString() {
    return source + " " + middle + " " + target + " " + target2;
  }

  @Override
  public String toNameStringE() {
    return SentenceHandler.sTableE.getName(source) + " "
        + SentenceHandler.sTableE.getName(middle) + " "
        + SentenceHandler.sTableE.getName(target) + " "
        + SentenceHandler.sTableE.getName(target2);
  }

  @Override
  public String toNameStringF() {
    return SentenceHandler.sTableF.getName(source) + " "
        + SentenceHandler.sTableF.getName(middle) + " "
        + SentenceHandler.sTableF.getName(target) + " "
        + SentenceHandler.sTableF.getName(target2);
  }

}
