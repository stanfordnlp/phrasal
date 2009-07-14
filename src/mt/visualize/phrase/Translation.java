package mt.visualize.phrase;

import java.util.*;

//TODO Re-factor the name
public class Translation {
  private final int id;
  private final String source;
  private final int numSourceWords;
//  private final String[] sourceTokens;
  private final PriorityQueue<Phrase> phrases;
  
  public Translation(int id, String source) {
    this.id = id;
    this.source = source;
    String[] sourceTokens = source.split("\\s+");
    numSourceWords = sourceTokens.length;
    phrases = new PriorityQueue<Phrase>();
  }

  //TODO For now, mangle the data model during layout creation
  public Phrase getBestPhrase() {
    return phrases.poll();
  }
  
  public int numPhrases() {
    return phrases.size();
  }
  
  public void addPhrase(double score, String english, String coverage) {
    Phrase phrase = new Phrase(english);
    phrase.setScore(score);
    
    coverage = coverage.replaceAll("\\{|\\}", "");
    String[] indices = coverage.split(",");
    if(indices.length == 1) {
      int index = Integer.parseInt(indices[0].trim());
      phrase.setSpan(index, index);
    } else {
      int start = Integer.parseInt(indices[0].trim());
      int end = Integer.parseInt(indices[indices.length - 1].trim());
      phrase.setSpan(start, end);
    }
    
    phrases.add(phrase);
  }
  
  /**
   * Returns a deep copy of the source string
   * @return
   */
  public String getSource() {
    return new String(source);
  }
  
  public int getNumSourceWords() {
    return numSourceWords;
  }
  
  public int getId() {
    return id;
  }
  
}
