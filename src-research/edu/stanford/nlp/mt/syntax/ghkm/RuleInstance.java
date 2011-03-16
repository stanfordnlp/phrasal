package edu.stanford.nlp.mt.syntax.ghkm;

import java.util.*;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.mt.train.WordAlignment;

/**
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class RuleInstance {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  Rule rule;
  StringNumberer stringNumberer;
  AlignmentTreeNode extractionNode;
  List<AlignmentTreeNode> childrenNodes;
  Map<Integer, AlignmentTreeNode> idx2lhs;
  Set<Integer> unalignedRHS = new HashSet<Integer>();
  int possibleCompositions = 0;

  public RuleInstance(AlignmentTreeNode extractionNode, BitSet compositions,
      WordAlignment sent, int lowFSpan, int highFSpan, StringNumberer num) {
    init(extractionNode, compositions, sent, lowFSpan, highFSpan, num);
  }

  protected void init(AlignmentTreeNode extractionNode, BitSet compositions,
      WordAlignment sent, int lowFSpan, int highFSpan, StringNumberer num) {
    assert (extractionNode.getLowFSpan() >= lowFSpan);
    assert (extractionNode.getHighFSpan() <= highFSpan);
    this.stringNumberer = num;
    if (DEBUG) {
      System.err.printf("RuleInstance: init: cat=%s span=%d-%d\n",
          extractionNode.label(), lowFSpan, highFSpan);
    }
    assert (rule == null);
    rule = new Rule(null, null, null, null);
    initLHS(extractionNode, compositions);
    initRHS(sent, lowFSpan, highFSpan);
  }

  public List<AlignmentTreeNode> getChildrenNodes() {
    return childrenNodes;
  }

  @Override
  public String toString() {
    return rule.toString();
  }

  /**
   * Create LHS of a minimal rule from node of an AlignmentGraph.
   */
  private void initLHS(AlignmentTreeNode extractionNode, BitSet compositions) {

    if (DEBUG)
      System.err.printf("RuleInstance: initLHS: generate LHS from node: %s\n",
          extractionNode.label());

    this.extractionNode = extractionNode;
    this.childrenNodes = new ArrayList<AlignmentTreeNode>();
    this.idx2lhs = new TreeMap<Integer, AlignmentTreeNode>();
    this.possibleCompositions = 0;

    Stack<AlignmentTreeNode> s = new Stack<AlignmentTreeNode>();
    List<Character> lhsStructList = new ArrayList<Character>();
    List<Integer> lhsLabelsList = new ArrayList<Integer>();
    boolean isFrontierNode = false;

    s.push(extractionNode);

    while (!s.isEmpty()) { // DFS

      AlignmentTreeNode curNode = s.pop();
      int numChildren = 0;
      boolean canStop = curNode.isFrontierNode() && curNode != extractionNode
          && !curNode.isLeaf();
      boolean doStop = canStop;

      if (canStop)
        doStop = !compositions.get(possibleCompositions++);

      if (doStop) {
        childrenNodes.add(curNode);
        idx2lhs.put(lhsLabelsList.size(), curNode);
        isFrontierNode = true;
      } else {
        numChildren = curNode.numChildren();
        assert (numChildren <= Character.MAX_VALUE);
        List<Tree> children = curNode.getChildrenAsList();
        for (int i = children.size() - 1; i >= 0; --i)
          s.push((AlignmentTreeNode) children.get(i));
      }

      lhsStructList.add((char) numChildren);
      lhsLabelsList.add(stringNumberer.getId(curNode.getNodeString()));

      if (DEBUG && !curNode.emptySpan())
        System.err
            .printf(
                "RuleInstance: initLHS: new node: cat=%s span=%d-%d is-frontier=%s\n",
                curNode.label(), curNode.getLowFSpan(), curNode.getHighFSpan(),
                isFrontierNode);

    }

    rule.lhsStruct = ArrayUtils.toPrimitive(lhsStructList
        .toArray(new Character[lhsStructList.size()]));
    rule.lhsLabels = ArrayUtils.toPrimitive(lhsLabelsList
        .toArray(new Integer[lhsLabelsList.size()]));
  }

  /**
   * Create RHS of a minimal rule from node of an AlignmentGraph.
   */
  @SuppressWarnings("unchecked")
  private void initRHS(WordAlignment sent, int lowFSpan, int highFSpan) {

    if (DEBUG)
      System.err.printf(
          "RuleInstance: initRHS: generate RHS from span %d-%d\n", lowFSpan,
          highFSpan);

    List<Pair<AlignmentTreeNode, Integer>> orderingList = new ArrayList<Pair<AlignmentTreeNode, Integer>>();
    for (int idx : idx2lhs.keySet())
      orderingList.add(new Pair(idx2lhs.get(idx), idx));

    Collections.sort(orderingList, new Comparator() {
      @Override
      public int compare(Object o1, Object o2) {
        AlignmentTreeNode n1 = (AlignmentTreeNode) ((Pair) o1).first(), n2 = (AlignmentTreeNode) ((Pair) o2)
            .first();
        if (n1.emptySpan())
          return -1;
        if (n2.emptySpan())
          return 1;
        return (Integer.valueOf(n1.getLowFSpan())).compareTo(n2.getLowFSpan());
      }
    });

    List<Integer> rhsLabelsList = new ArrayList<Integer>();
    BitSet lexical = new BitSet();
    int sz = orderingList.size();
    boolean firstNonNull = true;
    int prevH = lowFSpan - 1;

    List<Integer> lhsVars = new LinkedList<Integer>(), rhsVars = new LinkedList<Integer>();
    int maxRhsIdx = -1;

    for (int i = 0; i < sz; ++i) {

      AlignmentTreeNode curNode = orderingList.get(i).first();
      if (curNode.emptySpan())
        continue;
      int curL = curNode.getLowFSpan(), curH = curNode.getHighFSpan();

      // Add terminals located between RHS constituents:
      int first_fi = (firstNonNull) ? lowFSpan : prevH + 1;
      for (int fi = first_fi; fi < curL; ++fi) {
        if (sent.f2e(fi).isEmpty())
          unalignedRHS.add(rhsLabelsList.size());
        rhsLabelsList.add(sent.f().get(fi).getId());
        lexical.set(fi);
      }
      firstNonNull = false;

      // Add non-terminal to RHS:
      rhsLabelsList.add(stringNumberer.getId(curNode.getNodeString()));
      int lhsIdx = orderingList.get(i).second();
      int rhsIdx = rhsLabelsList.size() - 1;
      lhsVars.add(lhsIdx);
      rhsVars.add(rhsIdx);
      if (maxRhsIdx < rhsIdx)
        maxRhsIdx = rhsIdx;

      if (DEBUG) {
        System.err.printf("RuleInstance: initRHS: new RHS non-terminal: "
            + "cat=%s rhs-pos=%d lhs-pos=%d span=%d-%d", curNode.label(),
            rhsIdx, lhsIdx, curL, curH);
        if (prevH >= 0)
          System.err.printf(" prev-span-end=%d", prevH);
        System.err.println();
      }
      prevH = curH;
    }

    // Initialize variables:
    rule.clear_non_terminals(maxRhsIdx + 1);
    for (int i = 0; i < lhsVars.size(); ++i)
      rule.add_non_terminal(rhsVars.get(i), lhsVars.get(i));

    // Add unaligned words at end of RHS:
    for (int fi = prevH + 1; fi <= highFSpan; ++fi) {
      if (sent.f2e(fi).isEmpty())
        unalignedRHS.add(rhsLabelsList.size());
      rhsLabelsList.add(sent.f().get(fi).getId());
      lexical.set(fi);
    }
    rule.rhsLabels = ArrayUtils.toPrimitive(rhsLabelsList
        .toArray(new Integer[rhsLabelsList.size()]));
  }

  public List<Rule> getAllRHSVariants() {
    return rule.getAllRHSVariants(unalignedRHS);
  }

  public AlignmentTreeNode getExtractionNode() {
    return extractionNode;
  }
}
