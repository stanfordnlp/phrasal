package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.mt.base.IString;

import java.util.Set;
import java.util.HashSet;

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
    for (String line : ObjectBank.getLineIterator(fileName))
      for (String word : line.split("\\s+")) {
        IString w = new IString(word);
        filterWords.add(w.getId());
        if (DEBUG)
          System.err.printf("UnigramRuleFilter: adding word: %s\n",w);
      }
  }

  public boolean keep(Rule r) {
    for(int i=0; i<r.rhsLabels.length; ++i) {
      if (!r.is_lhs_non_terminal(i)) {
        int id = r.rhsLabels[i];
        if (DEBUG)
          System.err.printf("rhs el: %s\n",IString.getString(id));
        if (!filterWords.contains(id))
          return false;
      }
    }
    return true;
  }
}
