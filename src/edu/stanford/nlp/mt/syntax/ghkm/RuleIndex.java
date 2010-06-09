package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.util.FastIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;

import java.util.Iterator;

/**
 * Index and counts for GHKM rules and their LHS (target language) and RHS (source language).
 *
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class RuleIndex implements Iterable<RuleIndex.RuleId> {

  class RuleId {

    final Rule rule;
    final int lhsId, rhsId, rootId, ruleId;

    RuleId(Rule rule, int lhsId, int rhsId, int rootId, int ruleId) {
      this.rule = rule;
      this.lhsId = lhsId;
      this.rhsId = rhsId;
      this.rootId = rootId;
      this.ruleId = ruleId;
    }
  }

  class RuleIterator implements Iterator<RuleId> {
    Iterator<int[]> indexIterator;

    RuleIterator(DynamicIntegerArrayIndex ruleIndex) {
      this.indexIterator = ruleIndex.iterator();
    }

    @Override public boolean hasNext() { return indexIterator.hasNext(); }
    @Override public RuleId next() { return getRuleId(indexIterator.next()); }
    @Override public void remove() { throw new UnsupportedOperationException(); }
  }

  final DynamicIntegerArrayIndex
     lhsIndex = new DynamicIntegerArrayIndex(),
     rhsIndex = new DynamicIntegerArrayIndex(),
     ruleIndex = new DynamicIntegerArrayIndex();

  final Index<Integer> rootIndex = new FastIndex<Integer>();

  private RuleId getRuleId(int[] intA) {

    int lhsId = intA[0];
    int rhsId = intA[1];

    // Reconstruct RHS:
    int[] rhs = rhsIndex.get(rhsId);

    // Reconstruct LHS:
    int[] lhsWithStruct = lhsIndex.get(lhsId);
    int[] lhs = new int[lhsWithStruct[0]];
    System.arraycopy(lhsWithStruct,lhsWithStruct.length-lhs.length,lhs,0,lhs.length);

    // Reconstruct tree:
    char[] tree = new char[lhsWithStruct.length - lhs.length - 1];
    for (int i=0; i<tree.length; ++i)
      tree[i] = (char) lhsWithStruct[1+i];

    // Reconstruct vars:
    char[] vars = new char[intA.length-2];
    for (int i=0; i<vars.length; ++i)
      vars[i] = (char) intA[2+i];

    Rule r = new Rule(tree, vars, lhs, rhs);
    int ruleId = ruleIndex.indexOf(intA, true);
    int rootId = getRootId(r);
    return new RuleId(r, lhsId, rhsId, rootId, ruleId);
  }

  public RuleId getRuleId(Rule r) {

    int rootId = getRootId(r);
    int lhsId = getTreeLHSId(r);
    int rhsId = getRHSId(r);

    int[] intA = new int[2+r.rhs2lhs.length];
    intA[0] = lhsId;
    intA[1] = rhsId;

    for (int i=0; i<r.rhs2lhs.length; ++i)
      intA[2+i] = r.rhs2lhs[i];
    int id = ruleIndex.indexOf(intA, true);

    return new RuleId(r, lhsId, rhsId, rootId, id);
  }

  private int getRootId(Rule r) {
    synchronized(rootIndex) { return rootIndex.indexOf(r.lhsLabels[0], true); }
  }

  private int getTreeLHSId(Rule r) {
    int[] lhsArray = r.getTreeLHSIntArray();
    return lhsIndex.indexOf(lhsArray,true);
  }

  private int getRHSId(Rule r) {
    return rhsIndex.indexOf(r.rhsLabels,true);
  }

  public Iterator<RuleId> iterator() {
    return new RuleIterator(ruleIndex);
  }

  int size() {
    return ruleIndex.size();
  }

  String getSizeInfo() {
    StringBuffer buf = new StringBuffer();
    buf.append("rules=").append(ruleIndex.size());
    buf.append(", lhs=").append(lhsIndex.size());
    buf.append(", rhs=").append(rhsIndex.size());
    buf.append(", root=").append(rootIndex.size());
    return buf.toString();
  }
}
