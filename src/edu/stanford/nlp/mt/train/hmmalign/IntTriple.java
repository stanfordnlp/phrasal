package edu.stanford.nlp.mt.train.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class IntTriple extends IntTuple {
  private int source;
  private int middle;
  private int target;

  public IntTriple() {
//    numElements = 3;

  }

  public IntTriple(int src, int mid, int trgt) {
    source = src;
    target = trgt;
    middle = mid;
//    numElements = 3;
    //init();

  }


  @Override
	public IntTuple getCopy() {
    IntTriple nT = new IntTriple(source, middle, target);
    return nT;
  }


  @Override
	public void set(int num, int val) {
    switch (num) {
      case 0:
        {
          source = val;
        }
      case 1:
        {
          middle = val;
        }
      case 2:
        {
          target = val;
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
    return 0;


  }


  public int getSource() {
    return source;
  }

  public int getTarget() {
    return target;
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

  @Override
	public int hashCode() {
    return (source << 20) ^ (middle << 10) ^ (target);
  }

  @Override
	public boolean equals(Object o) {
    IntTriple iP = (IntTriple) o;
    return ((iP.source == source) && (iP.middle == middle) && (iP.target == target));

  }

  @Override
	public void print() {
    System.out.println(target + "|" + middle + "|" + source);
  }


  @Override
	public String toString() {

    return source + " " + middle + " " + target;

  }


  @Override
	public String toNameStringE() {

    return SentenceHandler.sTableE.getName(source) + " " + SentenceHandler.sTableE.getName(middle) + " " + SentenceHandler.sTableE.getName(target);

  }


  @Override
	public String toNameStringF() {

    return SentenceHandler.sTableF.getName(source) + " " + SentenceHandler.sTableF.getName(middle) + " " + SentenceHandler.sTableF.getName(target);


  }


}
