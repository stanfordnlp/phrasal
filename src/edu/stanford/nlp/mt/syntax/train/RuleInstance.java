package mt.syntax.train;

import mt.base.IString;
import mt.train.WordAlignment;

import java.util.*;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ArrayUtils;
import it.unimi.dsi.fastutil.chars.Char2CharArrayMap;
import it.unimi.dsi.fastutil.chars.CharArraySet;

/**
 * @author Michel Galley
 */
public class RuleInstance {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  Rule rule;
  AlignmentTreeNode extractionNode;
  List<AlignmentTreeNode> childrenNodes;
  Map<Integer, AlignmentTreeNode> idx2lhs;
  Set<Character> unalignedRHS = new CharArraySet();
  int possibleCompositions=0;

  public RuleInstance(AlignmentTreeNode extractionNode, BitSet compositions, WordAlignment sent, int lowFSpan, int highFSpan) {
    init(extractionNode, compositions, sent, lowFSpan, highFSpan);
  }

  protected void init(AlignmentTreeNode extractionNode, BitSet compositions, WordAlignment sent, int lowFSpan, int highFSpan) {
		assert(extractionNode.getLowFSpan() >= lowFSpan);
		assert(extractionNode.getHighFSpan() <= highFSpan);
		if(DEBUG) {
			System.err.printf("RuleInstance: init: cat=%s span=%d-%d\n",
           extractionNode.label(),lowFSpan,highFSpan);
		}
    assert(rule == null);
    rule = new Rule();
    initLHS(extractionNode, compositions);
    initRHS(sent, lowFSpan, highFSpan);
  }

  public List<AlignmentTreeNode> getChildrenNodes() {
    return childrenNodes;
  }

  @Override
	public String toString() { return rule.toString(); }

  /**
   * Create LHS of a minimal rule from node of an AlignmentGraph.
   */
  private void initLHS(AlignmentTreeNode extractionNode, BitSet compositions) {
		if(DEBUG)
			System.err.printf("RuleInstance: initLHS: generate LHS from node: %s\n", extractionNode.label());
    this.extractionNode = extractionNode;
    this.childrenNodes = new ArrayList<AlignmentTreeNode>();
    this.idx2lhs = new TreeMap<Integer,AlignmentTreeNode>();
    this.possibleCompositions=0;
    Stack<AlignmentTreeNode> s = new Stack<AlignmentTreeNode>();
    List<Character> lhsStructList = new ArrayList<Character>();
    List<Integer> lhsLabelsList = new ArrayList<Integer>();
    s.push(extractionNode);
    boolean isFrontierNode = false;
    while(!s.isEmpty()) { // DFS
      AlignmentTreeNode curNode = s.pop();
      int numChildren = 0;
      boolean canStop =
        curNode.isFrontierNode() && curNode != extractionNode && !curNode.isLeaf();
      boolean doStop = canStop;
      if(canStop)
        doStop = !compositions.get(possibleCompositions++);
      if(doStop) {
        childrenNodes.add(curNode);
        idx2lhs.put(lhsLabelsList.size(),curNode);
        isFrontierNode = true;
			} else {
        numChildren = curNode.numChildren();
        assert(numChildren <= Character.MAX_VALUE);
        List<Tree> children = curNode.getChildrenAsList();
        for(int i=children.size()-1; i>=0; --i)
          s.push((AlignmentTreeNode)children.get(i));
      }
      lhsStructList.add((char)numChildren);
      lhsLabelsList.add(new IString(curNode.getNodeString()).getId());
			if(DEBUG && !curNode.emptySpan())
				System.err.printf("RuleInstance: initLHS: new node: cat=%s span=%d-%d is-frontier=%s\n",
					curNode.label(), curNode.getLowFSpan(), curNode.getHighFSpan(), isFrontierNode);
    }
    rule.lhsStruct = ArrayUtils.toPrimitive(lhsStructList.toArray(new Character[lhsStructList.size()]));
    rule.lhsLabels = ArrayUtils.toPrimitive(lhsLabelsList.toArray(new Integer[lhsLabelsList.size()]));
  }


  /**
   * Create RHS of a minimal rule from node of an AlignmentGraph.
   */
	@SuppressWarnings("unchecked")
  private void initRHS(WordAlignment sent, int lowFSpan, int highFSpan) {
    Map rhs2lhs = new Char2CharArrayMap();
    if(DEBUG)
			System.err.printf("RuleInstance: initRHS: generate RHS from span %d-%d\n", lowFSpan, highFSpan);
    rule.rhs2lhs.clear();
    List<Pair<AlignmentTreeNode,Integer>> orderingList = new ArrayList<Pair<AlignmentTreeNode,Integer>>();
    for(int idx : idx2lhs.keySet())
      orderingList.add(new Pair(idx2lhs.get(idx),idx));
    Collections.sort(orderingList, new Comparator() {
      @Override
      public int compare(Object o1, Object o2) {
        AlignmentTreeNode n1 = (AlignmentTreeNode) ((Pair)o1).first(),
            n2 = (AlignmentTreeNode) ((Pair)o2).first();
			  if(n1.emptySpan()) return -1;
			  if(n2.emptySpan()) return 1;
        return (Integer.valueOf(n1.getLowFSpan())).compareTo(n2.getLowFSpan());
      }
    });
    List<Integer> rhsLabelsList = new ArrayList<Integer>();
		BitSet lexical = new BitSet();
    int sz = orderingList.size();
    boolean firstNonNull = true;
		int prevH = lowFSpan-1;
    for(int i=0; i<sz; ++i) {
      AlignmentTreeNode curNode = orderingList.get(i).first();
			if(curNode.emptySpan())
			  continue;
      int curL = curNode.getLowFSpan(), curH = curNode.getHighFSpan();
      // Add terminals lying between RHS constituents:
			int first_fi = (firstNonNull) ? lowFSpan : prevH+1;
			for(int fi=first_fi; fi<curL; ++fi) {
				if(sent.f2e(fi).isEmpty())
					unalignedRHS.add((char)rhsLabelsList.size());
				rhsLabelsList.add(sent.f().get(fi).getId());
				lexical.set(fi);
			}
      firstNonNull=false;
			// Add non-terminal to RHS:
			rhsLabelsList.add(new IString(curNode.getNodeString()).getId());
			int lhsIdx = orderingList.get(i).second();
			int rhsIdx = rhsLabelsList.size()-1;
			assert(lhsIdx < Character.MAX_VALUE);
			assert(rhsIdx < Character.MAX_VALUE);
			rhs2lhs.put((char)rhsIdx,(char)lhsIdx);
      if(DEBUG) {
				System.err.printf("RuleInstance: initRHS: new RHS non-terminal: "+
				 "cat=%s rhs-pos=%d lhs-pos=%d span=%d-%d",
				  curNode.label(),rhsIdx,lhsIdx,curL,curH);
				if(prevH >= 0) System.err.printf(" prev-span-end=%d",prevH);
				System.err.println();
			}
 			prevH = curH;
    }
	 	// Add unaligned words at end of RHS:
		for(int fi=prevH+1; fi<=highFSpan; ++fi) {
			if(sent.f2e(fi).isEmpty())
				unalignedRHS.add((char)rhsLabelsList.size());
			rhsLabelsList.add(sent.f().get(fi).getId());
			lexical.set(fi);
		}
    rule.rhsLabels = ArrayUtils.toPrimitive(rhsLabelsList.toArray(new Integer[rhsLabelsList.size()]));
    rule.rhs2lhs = rhs2lhs;
  }

  public List<Rule> getAllRHSVariants() {
    return rule.getAllRHSVariants(unalignedRHS);
  }

  public AlignmentTreeNode getExtractionNode() {
    return extractionNode;
  }
}
