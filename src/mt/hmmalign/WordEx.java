package mt.hmmalign;

/*
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class WordEx extends Word {
  private int index;
  private int count;

  public WordEx() {
  }

  public WordEx(int wordId, int tagId, int index, int count) {

    super(wordId, tagId);
    this.index = index;
    this.count = count;
  }


  public WordEx(int wordId, int tagId) {

    super(wordId, tagId);

  }

  public void incCount(int inc) {
    this.count += inc;
  }

  public int getCount() {
    return count;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  public String toNameStringE() {

    return SentenceHandler.sTableE.getName(index);

  }


  public String toNameStringF() {

    return SentenceHandler.sTableF.getName(index);

  }


  public void print() {
    System.out.print("Index " + index + " Count " + count + " ");
    super.print();

  }


}
