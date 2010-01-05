package mt.decoder.efeat;

import java.util.*;

public class ArabicKbestAnalysis implements Cloneable {
  public Map<Integer,Integer> subjects;
  public Set<Integer> verbs;
  public double logCRFScore;
  
  public ArabicKbestAnalysis() {
    subjects = new HashMap<Integer,Integer>();
    verbs = new HashSet<Integer>();
    logCRFScore = 0.0;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("logCRFScore: %f\n",logCRFScore));
    sb.append("Subjects: \n");
    for(Map.Entry<Integer, Integer> entry : subjects.entrySet())
      sb.append(String.format(" %d %d\n",entry.getKey(),entry.getValue()));
    sb.append("Verbs: \n");
    for(int verbIdx : verbs)
      sb.append(String.format(" %d\n",verbIdx));
    return sb.toString();
  }
  
  @Override
  public boolean equals(Object other) {
    if (this == other)
      return true;
    if (!(other instanceof ArabicKbestAnalysis))
      return false;
    
    ArabicKbestAnalysis o = (ArabicKbestAnalysis) other;
   
    return subjects.equals(o.subjects) && verbs.equals(o.verbs);
  }
  
  /**
   * Performs a deep copy
   */
  @Override
  public Object clone() {
    ArabicKbestAnalysis clonedObj = new ArabicKbestAnalysis();
    clonedObj.subjects = new HashMap<Integer,Integer>(subjects);
    clonedObj.verbs = new HashSet<Integer>(verbs);
    clonedObj.logCRFScore = logCRFScore;
    return clonedObj;
  }
}
