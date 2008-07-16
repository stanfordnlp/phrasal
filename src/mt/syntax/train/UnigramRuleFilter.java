package mt.syntax.train;

import edu.stanford.nlp.util.FileLines;

import java.util.Set;
import java.util.HashSet;

import edu.stanford.nlp.util.IString;

/**
 * Filter rules against words of a given corpus.
 *
 * @author Michel Galley
 */
public class UnigramRuleFilter {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  
  Set<Integer> filterWords = new HashSet<Integer>();

  public UnigramRuleFilter(String fileName) {
    for(String line : new FileLines(fileName))
      for(String word : line.split("\\s+")) {
        IString w = new IString(word);
        filterWords.add(w.getId());
        if(DEBUG)
          System.err.printf("UnigramRuleFilter: adding word: %s\n",w);
      }
  }

  public boolean keep(Rule r) {
    for(int i=0; i<r.rhsLabels.length; ++i) {
      if(r.rhs2lhs.get((char)i) == null) {
        int id = r.rhsLabels[i];
        if(DEBUG)
          System.err.printf("rhs el: %s\n",IString.getString(id));
        if(!filterWords.contains(id))
          return false;
      }
    }
    return true;
  }
}
