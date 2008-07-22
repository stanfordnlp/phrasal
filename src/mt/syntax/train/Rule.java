package mt.syntax.train;

import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.IString;

import java.util.*;

import it.unimi.dsi.fastutil.chars.Char2CharArrayMap;

/**
 * @author Michel Galley
 */
public class Rule {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String SYNCAT_RHS_PROPERTY = "SynCatRHS";
  public static final boolean SYNCAT_RHS = Boolean.parseBoolean(System.getProperty(SYNCAT_RHS_PROPERTY, "true"));

  static public final String MAX_UNALIGNED_RHS_OPT = "MaxUnalignedRHS";

  static public int MAX_UNALIGNED_RHS = 7;

  public static final int MISSING_RHS_EL_IDX = -1;

  char[] lhsStruct; // tree structure of lhs
  int[] lhsLabels, rhsLabels; // labels of lhs and rhs
  Map<Character,Character> rhs2lhs = new Char2CharArrayMap(); // alignment between vars of rhs and lhs

  /**
	 * Because of unaligned foreign words, there may be more than one minimal rule extractible
   * from a given tree node; this function returns all of them.
	 * To Avoid combinatorial explosion, this function returns just one minimal rule if the number of
   * unaligned foreign words appearing in the RHS of the rule is greater than a given threshold.
   * @arg unalignedRHS Set of indices of unaligned words in RHS
	 */
	public List<Rule> getAllRHSVariants(Set<Character> uRHS) {
    if(DEBUG) {
      System.err.printf("Rule: getAllRHSVariants: unalignedRHS:");
      for(int i : uRHS) {
        System.err.printf(" "+i);
      }
      System.err.println();
    }
    if(uRHS.size() > MAX_UNALIGNED_RHS) {
      // Too many unaligned words to account for in RHS:
      List<Rule> list = new ArrayList<Rule>();
			list.add(this);
      if(DEBUG)
        System.err.printf("Too many (%d) unaligned words in RHS of rule: %s\n",uRHS.size(),toString());
      return list;
		}
    Stack<UnalignedWordSet> openList = new Stack<UnalignedWordSet>();
    Set<UnalignedWordSet> closedList = new HashSet<UnalignedWordSet>();
    openList.push(new UnalignedWordSet(uRHS,uRHS,rhs2lhs,rhsLabels.length));
    List<Rule> rules = new ArrayList<Rule>();
    while(openList.size() > 0) {
      UnalignedWordSet curSet = openList.pop();
      Set usRHS = curSet.getSubset();
      if(closedList.contains(curSet))
        continue;
      if(!closedList.add(curSet))
        continue;
      for(UnalignedWordSet successorSet : curSet.getSizeMinusOneSets()) {
        if(!closedList.contains(successorSet))
          openList.push(successorSet);
      }
      // new rule with different assignments of unaligned foreign words:
      Rule newInst = new Rule();
      // No deep copy for lhsStruct and lhsLabels, in order to fit as
      // many rules as possible in memory, but could be dangerous (!):
      newInst.lhsStruct = lhsStruct;
      newInst.lhsLabels = lhsLabels;
      // Deep copy:
      int oldSz = rhsLabels.length;
      int newSz = oldSz-uRHS.size()+usRHS.size();
      newInst.rhsLabels = new int[newSz];
      newInst.rhs2lhs = new Char2CharArrayMap();
      for(int src=oldSz-1, tgt=newSz-1; tgt>=0; --src, --tgt) {
        while(uRHS.contains((char)src) && !usRHS.contains((char)src))
          --src;
        newInst.rhsLabels[tgt] = rhsLabels[src];
        if(rhs2lhs.containsKey((char)src)) {
          newInst.rhs2lhs.put((char)tgt,rhs2lhs.get((char)src));
        }
      }
      rules.add(newInst);
      if(DEBUG) {
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
		if(DEBUG)
			System.err.println("Rule: toString: "+buf.toString());
    return buf.toString();
  }

  public void printXrsLHS(StringBuffer buf) {
    printXrsLHS(buf, new MutableInteger(0), new MutableInteger(0));
  }

  private void printXrsLHS(StringBuffer buf, MutableInteger arrayIdx, MutableInteger varIdx) {
    int sz = lhsStruct[arrayIdx.intValue()];
    if(sz == 0)
      return;
    buf.append("(");
    for(int i=0; i<sz; ++i) {
      if(i>0) buf.append(" ");
      boolean isNT = (rhs2lhs.containsValue((char)(arrayIdx.intValue()+1)));
      if(isNT) {
        buf.append("x").append(varIdx).append(":");
        varIdx.incValue(1);
      }
      boolean isLex = (lhsStruct[arrayIdx.intValue()+1] == 0 && !isNT);
      if(isLex) buf.append("\"");
      buf.append(IString.getString(lhsLabels[arrayIdx.intValue()+1]));
      if(isLex) buf.append("\"");
      arrayIdx.incValue(1);
      printXrsLHS(buf,arrayIdx,varIdx);
    }
    buf.append(")");
  }

  private void printXrsRHS(StringBuffer buf) {
    List<Character> idxs = new ArrayList<Character>(rhs2lhs.values());
    Collections.sort(idxs);
    Map<Character,Character> idxToVar = new TreeMap<Character,Character>();
    int curVarIdx =-1;
    for(char idx : idxs)
      idxToVar.put(idx,(char)++curVarIdx);
    boolean first = true;
    for(int i=0; i<rhsLabels.length; ++i) {
			if(rhsLabels[i] == MISSING_RHS_EL_IDX)
				continue;
      boolean isNT = (rhs2lhs.containsKey((char)i));
      if(isNT) {
				char idx = rhs2lhs.get((char)i);
        int varIdx = idxToVar.get(idx);
        if(!first)
          buf.append(" ");
        buf.append("x").append(varIdx);
				if(SYNCAT_RHS)
					buf.append(":");
      } else {
        if(!first)
          buf.append(" ");
        buf.append("\"");
      }
      if(SYNCAT_RHS || !isNT)
				buf.append(IString.getString(rhsLabels[i]));
			if(!isNT)
        buf.append("\"");
      first = false;
    }
  }

  @Override
	public boolean equals(Object object) {
    if(object.getClass() != getClass())
      return false;
    Rule r = (Rule) object;
    boolean isEq =
     (Arrays.equals(lhsStruct,r.lhsStruct) &&
      Arrays.equals(lhsLabels,r.lhsLabels) &&
      Arrays.equals(rhsLabels,r.rhsLabels) &&
      rhs2lhs.equals(r.rhs2lhs));
    return isEq;
  }

  @Override
	public int hashCode() {
    return
     Arrays.hashCode(lhsStruct)+
     Arrays.hashCode(lhsLabels)+
     Arrays.hashCode(rhsLabels)+
     rhs2lhs.hashCode(); 
  }

  /**
   * Return an int array that uniquely determines the LHS of this rule.
   */
  public int[] getLHSIntArray() {
    int[] array = new int[lhsStruct.length+lhsLabels.length];
    for(int i=0; i<lhsStruct.length; ++i) {
      array[i] = lhsStruct[i];
    }
    System.arraycopy(lhsLabels,0,array,lhsStruct.length,lhsLabels.length);
    return array;
  }
}
