package edu.stanford.nlp.mt.syntax.ghkm;

import java.util.*;
import java.io.PrintStream;

/**
 * Class representing a set of unaligned foreign words
 * that may appear in RHS constituents of a rule.
 *
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class UnalignedWordSet {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private final Set<Integer> subsetWordIdx = new TreeSet<Integer>();
  private final Set<Integer> wordIdx;
  private final Rule rule;
  private final int rhsSize;

  public UnalignedWordSet(Rule rule, Set<Integer> wordIdx, Set<Integer> subsetWordIdx, int rhsSize) {
    this.subsetWordIdx.addAll(subsetWordIdx);
    this.wordIdx = wordIdx;
    this.rhsSize = rhsSize;
    this.rule = rule;
  }

  public Set<UnalignedWordSet> getSizeMinusOneSets() {
    Set<UnalignedWordSet> s = new HashSet<UnalignedWordSet>();
    for (int i : subsetWordIdx) {
      UnalignedWordSet newInst = new UnalignedWordSet(rule,wordIdx,subsetWordIdx,rhsSize);
			boolean canAssignLeft =
       (i == 0 || rule.is_rhs_non_terminal(i-1) ||
        (wordIdx.contains(i-1) && !subsetWordIdx.contains(i-1)));
      boolean canAssignRight =
       (i+1 == rhsSize || rule.is_rhs_non_terminal(i+1) ||
        (wordIdx.contains(i+1) && !subsetWordIdx.contains(i+1)));
      if (!canAssignLeft && !canAssignRight)
        continue;
      newInst.subsetWordIdx.remove(i);
      s.add(newInst);
    }
    return s;
  }

  public Set<Integer> getSubset() {
    return subsetWordIdx;
  }

  @Override
	public boolean equals(Object object) {
    if (object.getClass() != getClass())
      return false;
    UnalignedWordSet ws = (UnalignedWordSet) object;
    return subsetWordIdx.equals(ws.subsetWordIdx);
  }

  @Override
	public int hashCode() { return subsetWordIdx.hashCode(); }

  public void debug(PrintStream out) {
    out.printf("UnalignedWordSet: getSizeMinusOneSets: from:");
    for(int c : wordIdx) { System.err.print(" "+c); }
    System.err.printf(" to:");
    for(int c : subsetWordIdx) { System.err.print(" "+c); }
    System.err.println();
  }
}
