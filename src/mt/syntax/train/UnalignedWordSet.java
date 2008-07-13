package mt.syntax.train;

import it.unimi.dsi.fastutil.chars.CharAVLTreeSet;

import java.util.*;
import java.io.PrintStream;

/**
 * Class representing a set of unaligned foreign words
 * that may appear in RHS constituents of a rule.
 */
public class UnalignedWordSet {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private final Set<Character> subsetWordIdx = new CharAVLTreeSet();
  private final Set<Character> wordIdx;
  private final Map<Character,Character> rhs2lhs;
  private final int rhsSize;

  public UnalignedWordSet(Set<Character> wordIdx, Set<Character> subsetWordIdx, Map<Character,Character> rhs2lhs, int rhsSize) {
    this.subsetWordIdx.addAll(subsetWordIdx);
    this.wordIdx = wordIdx;
    this.rhsSize = rhsSize;
    this.rhs2lhs = rhs2lhs;
  }

  public Set<UnalignedWordSet> getSizeMinusOneSets() {
    Set<UnalignedWordSet> s = new HashSet<UnalignedWordSet>();
    for(int i : subsetWordIdx) {
      UnalignedWordSet newInst = new UnalignedWordSet(wordIdx,subsetWordIdx,rhs2lhs,rhsSize);
			boolean canAssignLeft =
       (i == 0 || rhs2lhs.containsKey((char)(i-1)) ||
        (wordIdx.contains((char)(i-1)) && !subsetWordIdx.contains((char)(i-1))));
      boolean canAssignRight =
       (i+1 == rhsSize || rhs2lhs.containsKey((char)(i+1)) ||
        (wordIdx.contains((char)(i+1)) && !subsetWordIdx.contains((char)(i+1))));
      if(!canAssignLeft && !canAssignRight)
        continue;
      newInst.subsetWordIdx.remove((char)i);
      s.add(newInst);
    }
    return s;
  }

  public Set getSubset() {
    return subsetWordIdx;
  }

  public boolean equals(Object object) {
    if(object.getClass() != getClass())
      return false;
    UnalignedWordSet ws = (UnalignedWordSet) object;
    return subsetWordIdx.equals(ws.subsetWordIdx);
  }

  public int hashCode() { return subsetWordIdx.hashCode(); }

  public void debug(PrintStream out) {
    out.printf("UnalignedWordSet: getSizeMinusOneSets: from:");
    for(int c : wordIdx) { System.err.print(" "+c); }
    System.err.printf(" to:");
    for(int c : subsetWordIdx) { System.err.print(" "+c); }
    System.err.println();
  }
}
