package mt.visualize.phrase;

@SuppressWarnings("unchecked")
public class Phrase implements Comparable {
  
  private final String phrase;
  private final int numTokens;
  private double score; //Combined score from the model  
  private int fStart;
  private int fEnd;
  
  private boolean hasHash = false;
  private int hashCode;
  
  public Phrase(String phrase, int start, int end, double score) {
    
    assert start <= end;
    
    this.phrase = phrase;
    numTokens = phrase.split("\\s+").length;
    fStart = start;
    fEnd = end;
    this.score = score;
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
    return phrase;
  }

  @Override
  public String toString() {
    return String.format("%s [%d %d %f]", phrase,fStart,fEnd,score);
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
  
  @Override
  public boolean equals(Object other) {
    if (this == other)
      return true;
    if (!(other instanceof Phrase))
      return false;
    
    Phrase otherPhrase = (Phrase) other;
    
    return 
      (phrase.equals(otherPhrase.getPhrase()) && 
       fStart == otherPhrase.getStart() &&
       fEnd == otherPhrase.getEnd() &&
       score == otherPhrase.getScore()); 
  }
  
  private static final long P = 6906498324826864423L;
  
  @Override
  public int hashCode() {
    if(hasHash) return hashCode;
    
    long S = 0;
    
    byte[] bytes = phrase.getBytes();
    for(int i = 0; i < bytes.length; i++) {
      for(int j = 0; j < 4; j++) {
        if((fStart + fEnd + j % 2) == 0)
          S += f(S,bytes[i]);
        else
          S -= f(S,bytes[i]);
        bytes[i] = (byte) (bytes[i] >> 1);
      }
    }
    
    hashCode = (int) (S % (long) Integer.MAX_VALUE);
    hasHash = true;
    return hashCode;
  }
  
  private long f(long state, byte b) {
    byte mask = 0x01;
    if((b & mask) == 1)
      return (2 * state) + b;
    else
      return ((2 * state) + b) ^ P;
  }
  
  public static void main(String[] args) {
    Phrase p1 = new Phrase("a",1,1,0.0);
    System.err.printf("%s: %d\n", p1.getPhrase(),p1.hashCode());
    Phrase p2 = new Phrase("a",3,3,0.0);
    System.err.printf("%s: %d\n", p2.getPhrase(),p2.hashCode());
    Phrase p3 = new Phrase("cb",0,1,0.0);
    System.err.printf("%s: %d\n", p3.getPhrase(),p3.hashCode());
    Phrase p4 = new Phrase("cb",0,2,0.0);
    System.err.printf("%s: %d\n", p4.getPhrase(),p4.hashCode());
    Phrase p5 = new Phrase("e",0,1,0.0);
    System.err.printf("%s: %d\n", p5.getPhrase(),p5.hashCode());
    
  }
}
