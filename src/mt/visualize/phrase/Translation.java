package mt.visualize.phrase;

import java.util.*;

public class Translation {
  private final int id;
  private final String source;
  private final int numSourceWords;
  private final PriorityQueue<Phrase> phrases;
  
  public Translation(int id, String source) {
    this.id = id;
    this.source = source;
    numSourceWords = source.split("\\s+").length;
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
    int start, end;
    
    coverage = coverage.replaceAll("\\{|\\}", "");
    String[] indices = coverage.split(",");
    if(indices.length == 0)
      return;
    else if(indices.length == 1)
      start = end = Integer.parseInt(indices[0].trim());
    else {
      start = Integer.parseInt(indices[0].trim());
      end = Integer.parseInt(indices[indices.length - 1].trim());
    }
    
    phrases.add(new Phrase(english,start,end,score));
  }
  
  public String getSource() {
    return source;
  }
  
  public int getNumSourceWords() {
    return numSourceWords;
  }
  
  public int getId() {
    return id;
  }
  
}
