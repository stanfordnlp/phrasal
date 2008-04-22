package mt.hmmalign;

/**
 * This class holds a word Id together with a tag Id
 * Later other fields might be added as well
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class Word {
  private int wordId;
  private int tagId;
  private int hashCode;
  static int noneTagId = 0;

  public Word() {
  };

  public Word(int wordId, int tagId) {
    this.wordId = wordId;
    this.tagId = tagId;
    init();
  }

  public Word(int wordId) {
    this.wordId = wordId;
    this.tagId = noneTagId;
    init();
  }

  public void set(int wordId, int tagId) {
    this.wordId = wordId;
    this.tagId = tagId;
    init();
  }


  private void init() {
    hashCode = wordId << 7 + tagId;
  }

  public int hashCode() {
    return hashCode;
  }

  public boolean equals(Object o) {

    if (o instanceof Word) {
      Word w1 = (Word) o;
      return ((wordId == w1.getWordId()) && (tagId == w1.getTagId()));
    } else {
      return false;
    }
  }


  public boolean isSimple() {
    return ((wordId == 0) || (tagId == 0));
  }

  public int getWordId() {
    return wordId;
  }

  public int getTagId() {
    return tagId;
  }

  public String toString() {
    return wordId + "_" + tagId;

  }


  public String toNameString() {
    return null;

  }


  /* Print the word and tag separated with a _ and also print the hashcode in brackets */
  public void print() {
    System.out.print(wordId);
    System.out.print(SentenceHandler.UNDRSCR);
    System.out.print(tagId);
    System.out.print('(');
    System.out.print(hashCode);
    System.out.print(')');

  }


}
