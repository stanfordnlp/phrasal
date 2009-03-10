package mt.syntax.train;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import mt.base.DynamicIntegerArrayIndex;

import java.util.Collection;

/**
 * Index and counts for GHKM rules and their LHS (target language) and RHS (source language).
 *
 * @author Michel Galley
 */
public class RuleIndex {

  final DynamicIntegerArrayIndex
     lhsIndex = new DynamicIntegerArrayIndex(),
     rhsIndex = new DynamicIntegerArrayIndex();

  final Index<Rule> ruleIndex = new HashIndex<Rule>();
  final Index<Integer> rootIndex = new HashIndex<Integer>();

  public int getRuleId(Rule r) {
    return ruleIndex.indexOf(r, true);
  }

  public int getRootId(Rule r) {
    return rootIndex.indexOf(r.lhsLabels[0], true);
  }

  public int getLHSId(Rule r) {
    int[] lhsArray = r.getLHSIntArray();
    return lhsIndex.indexOf(lhsArray,true);
  }

  public int getRHSId(Rule r) {
    return rhsIndex.indexOf(r.rhsLabels,true);
  }
  
  public Collection<Rule> getCollection() {
    return ruleIndex.objectsList();
  }

  public String getSizeInfo() {
    StringBuffer buf = new StringBuffer();
    buf.append("rules=").append(ruleIndex.size());
    buf.append(", lhs=").append(lhsIndex.size());
    buf.append(", rhs=").append(rhsIndex.size());
    buf.append(", root=").append(rootIndex.size());
    return buf.toString();
  }
}
