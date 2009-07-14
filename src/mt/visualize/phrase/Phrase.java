package mt.visualize.phrase;

public class Phrase implements Comparable {
  
  private final String phrase;
  private final int numTokens;
  private double score; //Combined score from the model  
  private int fStart;
  private int fEnd;
  
  public Phrase(String phrase) {
    this.phrase = phrase;
    String[] toks = phrase.split("\\s+");
    numTokens = toks.length;
  }
  
  public void setSpan(int start, int end) {
    fStart = start;
    fEnd = end;
  }
  
  public int getStart() {
    return fStart;
  }
  
  public int getEnd() {
    return fEnd;
  }
  
  public void setScore(double s) {
    score = s;
  }
  
  public double getScore() {
    return score;
  }
  
  public int numTokens() {
    return numTokens;
  }
  
  public String getPhrase() {
    return new String(phrase);
  }

  /**
   * Note that this implementation is precisely the inverse of the specified
   * behavior of the Comparable interface. Using this implementation, the highest
   * scoring phrase will appear at the head of a {@link java.util.PriorityQueue}. 
   */
  public int compareTo(Object o) {
    Phrase rhs = (Phrase) o;
    
    if(score < rhs.getScore())
      return 1;
    else if(score > rhs.getScore())
      return -1;
    
    return 0;
  }
}
