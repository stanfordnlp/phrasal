package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.mt.base.IString;

import java.util.*;

/**
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class Rule {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

	// Whether to print syntactic constituents in the rhs of each rule (e.g., x0:NP instead of just x0)
  public static final String SYNCAT_RHS_PROPERTY = "SynCatRHS";
  public static final boolean SYNCAT_RHS = Boolean.parseBoolean(System.getProperty(SYNCAT_RHS_PROPERTY, "false"));

  static final String MAX_UNALIGNED_RHS_OPT = "MaxUnalignedRHS";
  static final char UNALIGNED = Character.MAX_VALUE;
  static final int MISSING_RHS_EL_IDX = -1;

  static int MAX_UNALIGNED_RHS = 7;

  // Compact representation of a GHKM rule:
  // Note: it would probably be safe to move from char to byte (i.e., 16 to 8 bits)
  char[] lhsStruct; // tree structure (LHS)
  char[] rhs2lhs; // non-terminals (maps RHS to LHS)
  int[] lhsLabels, rhsLabels; // labels of LHS and RHS

  Rule(char[] lhsStruct, char[] rhs2lhs, int[] lhsLabels, int[] rhsLabels) {
    this.lhsStruct = lhsStruct;
    this.rhs2lhs = rhs2lhs;
    this.lhsLabels = lhsLabels;
    this.rhsLabels = rhsLabels;
  }

  /**
	 * Because of unaligned foreign words, there may be more than one minimal rule extractible
   * from a given tree node; this function returns all of them.
	 * To Avoid combinatorial explosion, this function returns just one minimal rule if the number of
   * unaligned foreign words appearing in the RHS of the rule is greater than a given threshold.
   * @param uRHS Set of indices of unaligned words in RHS
	 */
	public List<Rule> getAllRHSVariants(Set<Integer> uRHS) {

    if (DEBUG) {
      System.err.printf("Rule: getAllRHSVariants: unalignedRHS:");
      for (int i : uRHS)
        System.err.printf(" "+i);
      System.err.println();
    }

    if (uRHS.size() > MAX_UNALIGNED_RHS) {
      // Too many unaligned words to account for in RHS:
      List<Rule> list = new ArrayList<Rule>();
			list.add(this);
      if (DEBUG)
        System.err.printf("Too many (%d) unaligned words in RHS of rule: %s\n",uRHS.size(),toString());
      return list;
		}

    Stack<UnalignedWordSet> openList = new Stack<UnalignedWordSet>();
    Set<UnalignedWordSet> closedList = new HashSet<UnalignedWordSet>();
    openList.push(new UnalignedWordSet(this,uRHS,uRHS,rhsLabels.length));
    List<Rule> rules = new ArrayList<Rule>();

    while (!openList.isEmpty()) {

      UnalignedWordSet curSet = openList.pop();
      Set<Integer> usRHS = curSet.getSubset();
      if (closedList.contains(curSet))
        continue;
      if (!closedList.add(curSet))
        continue;
      for (UnalignedWordSet successorSet : curSet.getSizeMinusOneSets()) {
        if (!closedList.contains(successorSet))
          openList.push(successorSet);
      }

      // Deep copy:
      int oldSz = rhsLabels.length;
      int newSz = oldSz-uRHS.size()+usRHS.size();

      // New rule with different assignments of unaligned foreign words:
      Rule newInst = new Rule(lhsStruct, null, lhsLabels, new int[newSz]);
      newInst.clear_non_terminals(newSz);

      for (int src=oldSz-1, tgt=newSz-1; tgt>=0; --src, --tgt) {
        while (uRHS.contains(src) && !usRHS.contains(src))
          --src;
        newInst.rhsLabels[tgt] = rhsLabels[src];
        if (is_rhs_non_terminal(src))
          newInst.add_non_terminal(tgt,get_lhs_non_terminal(src));
      }

      rules.add(newInst);

      if (DEBUG) {
        curSet.debug(System.err);
        newInst.toString();
      }
    }

    return rules;
  }

  @Override
	public String toString() {
    return toXrsString();
  }

  public String toXrsString() {
    StringBuffer buf = new StringBuffer();
    buf.append(IString.getString(lhsLabels[0]));
    printXrsLHS(buf);
    buf.append(" -> ");
    printXrsRHS(buf);
		if (DEBUG)
			System.err.println("Rule: toString: "+buf.toString());
    return buf.toString();
  }

  public String toJoshuaLHS() {
    StringBuffer buf = new StringBuffer();
    printJoshuaLHS(buf);
		if (DEBUG)
			System.err.println("Rule: toString: "+buf.toString());
    return buf.toString();
  }

  public String toJoshuaRHS() {
    StringBuffer buf = new StringBuffer();
    printJoshuaRHS(buf);
		if (DEBUG)
			System.err.println("Rule: toString: "+buf.toString());
    return buf.toString();
  }

  public void printXrsLHS(StringBuffer buf) {
    printXrsLHS(buf, new MutableInteger(0), new MutableInteger(0));
  }

  private void printXrsLHS(StringBuffer buf, MutableInteger arrayIdx, MutableInteger varIdx) {
    int sz = lhsStruct[arrayIdx.intValue()];
    if (sz == 0)
      return;
    buf.append("(");
    for (int i=0; i<sz; ++i) {
      if(i>0) buf.append(" ");
      int arrayPos = arrayIdx.intValue()+1;
      boolean isNT = (is_lhs_non_terminal(arrayPos));
      if (isNT) {
        buf.append("x").append(varIdx).append(":");
        varIdx.incValue(1);
      }
      boolean isLex = (lhsStruct[arrayPos] == 0 && !isNT);
      if(isLex) buf.append("\"");
      buf.append(IString.getString(lhsLabels[arrayPos]));
      if(isLex) buf.append("\"");
      arrayIdx.incValue(1);
      printXrsLHS(buf,arrayIdx,varIdx);
    }
    buf.append(")");
  }

  private void printXrsRHS(StringBuffer buf) {
    List<Character> idxs = new ArrayList<Character>();
    for (char c : get_lhs_non_terminals())
      if (c != UNALIGNED)
        idxs.add(c);
    Collections.sort(idxs);
    Map<Integer,Integer> idxToVar = new TreeMap<Integer,Integer>();
    int curVarIdx =-1;
    for (int idx : idxs)
      idxToVar.put(idx,++curVarIdx);
    boolean first = true;
    for (int i=0; i<rhsLabels.length; ++i) {
			if (rhsLabels[i] == MISSING_RHS_EL_IDX)
				continue;
      boolean isNT = is_rhs_non_terminal(i);
      if (!first)
        buf.append(" ");
      if (isNT) {
				int idx = get_lhs_non_terminal(i);
        int varIdx = idxToVar.get(idx);
        buf.append("x").append(varIdx);
				if (SYNCAT_RHS)
					buf.append(":");
      } else {
        buf.append("\"");
      }
      if (SYNCAT_RHS || !isNT)
				buf.append(IString.getString(rhsLabels[i]));
			if (!isNT)
        buf.append("\"");
      first = false;
    }
  }

  public void printJoshuaRHS(StringBuffer buf) {
    Map<Integer,Integer> idxToVar = new TreeMap<Integer,Integer>();
    int curVarIdx = 0;
    for (char c : get_lhs_non_terminals())
      if (c != UNALIGNED)
        idxToVar.put((int)c,++curVarIdx);
    printJoshuaRHS(buf, new MutableInteger(0), idxToVar);
  }

  private void printJoshuaRHS(StringBuffer buf, MutableInteger arrayIdx, Map<Integer,Integer> idxToVar) {
    int sz = lhsStruct[arrayIdx.intValue()];
    if (sz == 0)
      return;
    for (int i=0; i<sz; ++i) {
      if (i>0) buf.append(" ");
      int arrayPos = arrayIdx.intValue()+1;
      boolean isNT = is_lhs_non_terminal(arrayPos);
      boolean isLex = (lhsStruct[arrayPos] == 0 && !isNT);
      String tok = IString.getString(lhsLabels[arrayPos]);
      if (isNT) {
        buf.append("[").append(tok).append(",").append(idxToVar.get(arrayPos)).append("]");
      } else if(isLex) {
        buf.append(tok);
      }
      arrayIdx.incValue(1);
      printJoshuaRHS(buf,arrayIdx,idxToVar);
    }
  }

  private void printJoshuaLHS(StringBuffer buf) {
    int curVarIdx = 0;
    boolean first = true;
    for(int i=0; i<rhsLabels.length; ++i) {
			if (rhsLabels[i] == MISSING_RHS_EL_IDX)
				continue;
      boolean isNT = is_rhs_non_terminal(i);
      if (!first)
        buf.append(" ");
      if (isNT) {
        buf.append("[");
        buf.append(IString.getString(rhsLabels[i]));
        buf.append(",").append(++curVarIdx).append("]");
      } else {
        buf.append(IString.getString(rhsLabels[i]));
      }
      first = false;
    }
  }

  @Override
	public boolean equals(Object object) {
    assert(object instanceof Rule);
    Rule r = (Rule) object;
    return
     (Arrays.equals(lhsStruct,r.lhsStruct) &&
      Arrays.equals(lhsLabels,r.lhsLabels) &&
      Arrays.equals(rhsLabels,r.rhsLabels) &&
      Arrays.equals(rhs2lhs,r.rhs2lhs));
  }

  @Override
	public int hashCode() {
    return
     Arrays.hashCode(new int[] {
       Arrays.hashCode(lhsStruct),
       Arrays.hashCode(lhsLabels),
       Arrays.hashCode(rhsLabels),
       Arrays.hashCode(rhs2lhs) });
  }

  /**
   * Return an int array that uniquely determines the LHS of this rule.
   */
  public int[] getTreeLHSIntArray() {
    int[] array = new int[1+lhsStruct.length+lhsLabels.length];
    array[0] = lhsLabels.length;
    for (int i=0; i<lhsStruct.length; ++i) {
      array[i+1] = lhsStruct[i];
    }
    System.arraycopy(lhsLabels,0,array,lhsStruct.length+1,lhsLabels.length);
    return array;
  }

  //////////////////////////
  // Non-terminals:
  //////////////////////////

  public void clear_non_terminals(int sz) {
    rhs2lhs = new char[sz];
    Arrays.fill(rhs2lhs,UNALIGNED);
  }

  public char[] get_lhs_non_terminals() {
    return rhs2lhs;
  }

  public int get_lhs_non_terminal(int rhsIdx) {
    return rhs2lhs[rhsIdx];
  }

  public boolean is_rhs_non_terminal(int rhsIdx) {
    return rhsIdx < rhs2lhs.length && rhs2lhs[rhsIdx] != UNALIGNED;
  }

  public boolean is_lhs_non_terminal(int lhsIdx) {
    for (char el : rhs2lhs) if (el == lhsIdx) return true; return false;
  }

  public void add_non_terminal(int rhsIdx, int lhsIdx) {
    assert (lhsIdx < UNALIGNED);
    assert (rhsIdx < UNALIGNED);
    rhs2lhs[rhsIdx] = (char)lhsIdx;
  }

}
