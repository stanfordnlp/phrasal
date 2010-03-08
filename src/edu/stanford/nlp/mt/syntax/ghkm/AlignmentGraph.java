package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

import java.util.*;
import java.io.IOException;


/**
 * A graph containing two types of edges: (1) edges of an English parse tree
 * and (2) word alignments between a French sentence and leaf nodes of the
 * English tree. The English tree (class {@link AlignmentTreeNode})
 * contains annotation of [...]. This annotation lets us determine "sensible"
 * frontiers of the parse tree along which constituents can be reordered.
 * It is assumed that the graph is directed and its edges are looking downwards
 * (in particular, directed from English words to French words). The method for
 *  detecting these crossings is a linear-time algorithm described in
 * (Galley, Hopkins, Knight, Marcu, 2004).
 *
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class AlignmentGraph {

  public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  static boolean reversedAlignment = true;
  static boolean oneIndexedAlignment = false;

  private static final BitSet NO_COMPOSITIONS = new BitSet();
  private static int maxCompositions = 0;

  AlignmentTreeNode eTree;
  List<AlignmentTreeNode> eLeaves;
  List<FeatureExtractor> extractors;
  SymmetricalWordAlignment align = new SymmetricalWordAlignment();

  AlignmentGraph() {}

  void init(String aString, String fString, String eTreeString, String eString) throws IOException {

    eTree = new AlignmentTreeNode(Tree.valueOf(eTreeString),null);
    eLeaves = new ArrayList<AlignmentTreeNode>();

    for (Tree l : eTree.getLeaves())
      eLeaves.add((AlignmentTreeNode)l);

    if (eString != null)
      checkEStringAgainstLeaves(eString);

    String yield = eTree.yield().toString();
    synchronized (IString.class) {
      align.init(fString, yield, aString, reversedAlignment, oneIndexedAlignment);
    }
    setFrontierNodes();
  }

  private void checkEStringAgainstLeaves(String eString) {

    String[] eWords = eString.split("\\s+");

    if (eWords.length != eLeaves.size() && eWords.length+1 != eLeaves.size())
      throw new RuntimeException
        ("AlignmentGraph: Mismatch between:\nleaves="+eLeaves.toString()+"\nestring="+eString);

    for (int i=0; i<eWords.length; ++i) {
      if (!eWords[i].matches("[\\(\"\\)\\[\\]\\{\\}\\*\\/]")) {
        if (!eWords[i].toLowerCase().equals(eLeaves.get(i).label().toString().toLowerCase())) {
          System.err.printf
            ("AlignmentGraph: mismatch between target-language corpus and parsed corpus: %s != %s\n",
             eLeaves.get(i).label(),eWords[i]); 
        }
      }
    }
  }

  public static void setMaxCompositions(int m) {
    maxCompositions = m;
  }

  private void setFrontierNodes() {

    if (eLeaves.size() != align.e().size()) {
      System.err.println(align.e().toString());
      throw new RuntimeException
          ("AlignmentGraph: setFrontierNodes: length mismatch: "+eLeaves.size()+" != "+align.e().size());
    }

    for (int i=0; i<eLeaves.size(); ++i) {
      AlignmentTreeNode n = eLeaves.get(i);
      for (int fi : align.e2f(i))
        n.addToFSpan(fi);
    }

    eTree.setFSpans();
    eTree.setFComplementSpans();
    eTree.setFrontierNodes();
  }

  /**
   * Extract minimal and composed rules from an alignment graph.
   */
  public Set<Rule> extractRules(StringNumberer num) {

    Set<Rule> sentenceRuleSet = new HashSet<Rule>();
		Stack<AlignmentTreeNode> nodesStack = new Stack<AlignmentTreeNode>();
    Set<AlignmentTreeNode> nodesSet = new HashSet<AlignmentTreeNode>();
    Stack<BitSet> compositionStack = new Stack<BitSet>();
    Set<BitSet> compositionSet = new HashSet<BitSet>();
    nodesStack.push(eTree);

	  while (!nodesStack.isEmpty()) {

      // For each node n of the frontier graph:
      AlignmentTreeNode n = nodesStack.pop();
      int low = n.getLowFSpan();
      int high = n.getHighFSpan();

      while(low > 0 && align.f2e(low-1).isEmpty()) --low;
      while(high+1 < align.f().size() && align.f2e(high+1).isEmpty()) ++high;

      // Create all possible compositions until maxCompositions is reached:
      compositionStack.clear();
      compositionStack.push(NO_COMPOSITIONS);
      compositionSet.clear();

      while (!compositionStack.isEmpty()) {

        BitSet composition = compositionStack.pop();
        RuleInstance rInst = new RuleInstance(n,composition,align,low,high,num);

        if (composition.equals(NO_COMPOSITIONS))
          for (FeatureExtractor extractor : extractors)
            extractor.extractFeatures(rInst);

        if (n.minimalRule == null)
          n.minimalRule = rInst;

        if (composition.cardinality() < maxCompositions) {
          for (int i=0; i<rInst.possibleCompositions; ++i) {
            if (!composition.get(i)) {
              BitSet nextComposition = (BitSet) composition.clone();
              nextComposition.set(i);
              if (compositionSet.contains(nextComposition))
                continue;
              compositionSet.add(nextComposition);
              compositionStack.push(nextComposition);
            }
          }
        }

        sentenceRuleSet.addAll(rInst.getAllRHSVariants());
        List<AlignmentTreeNode> childrenNodes = rInst.getChildrenNodes();

        if(DEBUG)
          System.err.println("AlignmentGraph: extractRules: building all RHS variations of rule: "+rInst.toString());

        for (AlignmentTreeNode childNode : childrenNodes) {
          if (!childNode.isLeaf()) {
            if (nodesSet.contains(childNode))
              continue;
            nodesStack.push(childNode);
            nodesSet.add(childNode);
          }
        }
      }
    }

    return sentenceRuleSet;
  }

  public void setFeatureExtractors(List<FeatureExtractor> e) {
    extractors = e;
  }
}
