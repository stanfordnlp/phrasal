package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.CategoryWordTag;

import java.util.Set;
import java.util.TreeSet;

/**
 * An English tree (class {@link AlignmentTreeNode}) that
 * contains annotation of the "crossings" (Fox, 2002), i.e.,
 * cases where RHS consituents of a given CFG production have English yields that
 * align to overlapping Chinese phrases (i.e., there is no sensible way of
 * reordering these consituents).
 *
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class AlignmentTreeNode extends Tree {

	private static final long serialVersionUID = 1L;

	public static final String DEBUG_PROPERTY = "DebugGHKM";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  protected static final HeadFinder COLLINS_HEAD_FINDER = new CollinsHeadFinder();

  /**
   * A leaf node should have a zero-length array for its
   * children. For efficiency, subclasses can use this array as a
   * return value for children() for leaf nodes if desired. Should
   * this be public instead?
   */
  protected static final AlignmentTreeNode[] ZERO_ATN_CHILDREN
      = new AlignmentTreeNode[0];

  /**
   * Label of this node.
   */
  protected CategoryWordTag label;

  /**
   * Daughters of this node.
   */
  protected AlignmentTreeNode[] children;

  /**
   * Parent of this node.
   */
  protected AlignmentTreeNode parent;

  /*
   * Spans of French words reachable from current node of the frontier graph.
   */
  protected TreeSet<Integer> fSpan = new TreeSet<Integer>();

  /*
   * Spans of French words reachable from any node of the frontier graph that is
   * neither a ancestor nor a dscendant of the current node. If fSpan and
   * fComplementSpan no not overlap, then there is no syntactic crossing.
   *
   */
  protected Set<Integer> fComplementSpan = new TreeSet<Integer>();

  /**
   * See definition of frontier node in (Galley et al., 2004).
   */
  protected boolean frontierNode = false;

  /**
   * Minimal rule rooted at current node
   * (there may be none).
   */
  protected RuleInstance minimalRule = null;

  /**
   * Constructor assigning parent, children, syntactic label,
   * head word, and head POS to each node.
   */
  protected AlignmentTreeNode(Tree t, AlignmentTreeNode parent) {

    this.parent = parent;
    Tree[] tChildren = t.children();
    int numChildren = tChildren.length;
    setLabel(new CategoryWordTag(t.label()));

    if (numChildren > 0) {
      Tree tPos = t.headPreTerminal(COLLINS_HEAD_FINDER);
      if(tPos != null) {
        label.setTag(tPos.label().value());
        label.setWord(tPos.getChild(0).label().value());
      }
    }

    children = new AlignmentTreeNode[numChildren];
    for (int i = 0; i < numChildren; i++)
      children[i] = new AlignmentTreeNode(tChildren[i], this);
  }

  /**
	 * Check if there is no syntactic crossing at the given constituent,
   * i.e., the node is along any "sensible" frontier of the FrontierGraph,
	 * i.e., if a MimimalRule is rooted at current node.
   */
  protected void setFrontierNode() {

    if (fSpan.isEmpty()) {
      frontierNode = false;
      return;
    }
    frontierNode = true;

    for (int i = fSpan.first(); i <= fSpan.last(); ++i) {
      if (fComplementSpan.contains(i)) {
        frontierNode = false;
        break;
      }
    }

		if (DEBUG && frontierNode)
			System.err.printf("AlignmentTreeNode: setFrontierNode: "+
			  "new frontier node cat=%s span=%s complement-span=%s\n",
			  label,fSpan.toString(),fComplementSpan.toString());
  }

  /**
   * Add foreign-word index to fSpan.
   */
  public void addToFSpan(int idx) {
    fSpan.add(idx);
  }

  /**
   * Add foreign-word index to fSpan.
   */
  public void addToFComplementSpan(int idx) {
    fComplementSpan.add(idx);
  }

  /**
   * Return indices of all foreign words reachable from current node.
   */
  public Set<Integer> getFSpan() {
    return fSpan;
  }

  /**
   * Return true if foreign span is empty.
   */
  public boolean emptySpan() { return fSpan.isEmpty(); }

  /*
   * Lowest value of the foreign span.
   */
  public int getLowFSpan() { return fSpan.first(); }

  /*
  * Highest value of the foreign span.
  */
  public int getHighFSpan() { return fSpan.last(); }

  /**
   * Return indices of all foreign words reachable from any node neither
   * ancestor nor successor in alignment graph.
   */
  public Set<Integer> getFComplementSpan() {
    return fComplementSpan;
  }

  /**
   * Returns true if current node is a frontier node as defined in
   * (Galley et al., 2004).
   */
  public boolean isFrontierNode() {
    return frontierNode;
  }

  /**
   * Returns an array of children for the current node, or null
   * if it is a leaf.
   */
  @Override
	public Tree[] children() {
    return children;
  }

  /**
   * Return label of current node (lowercase if leaf).
   */
  public String getNodeString() {
    if (isLeaf())
      return ((CategoryWordTag)label()).category().toLowerCase();
    return ((CategoryWordTag)label()).category();
  }

  /**
   * Return label of current node (lowercase if leaf).
   */
  public static String getNodeString(Tree n) {
    if (n.isLeaf())
      return ((CategoryWordTag)n.label()).category().toLowerCase();
    return ((CategoryWordTag)n.label()).category();
  }

  /**
   * Sets the children of this <code>Tree</code>.  If given
   * <code>null</code>, this method prints a warning and sets the
   * Tree's children to the canonical zero-length Tree[] array.
   * Constructing a LabeledScoredTreeLeaf is preferable in this
   * case.
   *
   * @param children An array of child trees
   */
  @Override
	public void setChildren(Tree[] children) {
    if (children == null) {
      System.err.println
           ("Warning -- you tried to set the children of a LabeledScoredTreeNode to null.\n"+
            "You really should be using a zero-length array instead.\n"+
            "Consider building a LabeledScoredTreeLeaf instead.");
      this.children = ZERO_ATN_CHILDREN;
    } else {
      this.children = (AlignmentTreeNode[]) children;
    }
  }

  /**
   * Returns the label associated with the current node, or null
   * if there is no label
   */
  @Override
	public Label label() {
    return label;
  }

  /**
   * Sets the label associated with the current node, if there is one.
   */
  public void setLabel(final CategoryWordTag label) {
    this.label = label;
  }

  /**
   * Appends the printed form of a parse tree (as a bracketed String)
   * to a <code>StringBuffer</code>.
   *
   * @return StringBuffer returns the <code>StringBuffer</code> @param sb
   */
  @Override
  public StringBuilder toStringBuilder(StringBuilder sb) {
    sb.append('(');
    sb.append(nodeString());
    for (Tree daughterTree : children) {
      sb.append(' ');
      daughterTree.toStringBuilder(sb);
    }
    return sb.append(')');
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * same type as the current <code>Tree</code>.  That is, this
   * implementation, will produce trees of type
   * <code>LabeledScoredTree(Node|Leaf)</code>.
   * The <code>Label</code> of <code>this</code>
   * is examined, and providing it is not <code>null</code>, a
   * <code>LabelFactory</code> which will produce that kind of
   * <code>Label</code> is supplied to the <code>TreeFactory</code>.
   * If the <code>Label</code> is <code>null</code>, a
   * <code>StringLabelFactory</code> will be used.
   * The factories returned on different calls a different: a new one is
   * allocated each time.
   *
   * @return a factory to produce labeled, scored trees
   */
  @Override
	public TreeFactory treeFactory() {
    LabelFactory lf;
    if (label() != null) {
      lf = label().labelFactory();
    } else {
      lf = StringLabel.factory();
    }
    return new LabeledScoredTreeFactory(lf);
  }

  /**
   * Set the foreign-language span of all nodes of AlignmentGraph.
   */
  public void setFSpans() {
    for (AlignmentTreeNode c : children) {
      c.setFSpans();
      for (int fi : c.getFSpan())
        addToFSpan(fi);
    }
  }

  /**
   * Set the foreign-language span of all nodes of AlignmentGraph.
   */
  public void setFComplementSpans() {

    for (AlignmentTreeNode c1 : children)
      for (int fi : c1.getFSpan())
        for (AlignmentTreeNode c2 : children)
          if (c1 != c2)
            c2.addToFComplementSpan(fi);

    for (AlignmentTreeNode c : children) {
      for (int fi : getFComplementSpan())
        c.addToFComplementSpan(fi);
      c.setFComplementSpans();
    }
  }

  public void setFrontierNodes() {
    for (Tree node : this)
      ((AlignmentTreeNode)node).setFrontierNode();
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class TreeFactoryHolder {
    static final TreeFactory tf = new LabeledScoredTreeFactory();
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>LabeledScoredTree{Node|Leaf}</code> type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tf;
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>LabeledScoredTree{Node|Leaf}</code> type, with
   * the <code>Label</code> made with the supplied
   * <code>LabelFactory</code>.
   * The factory returned is a different one each time
   *
   * @param lf The LabelFactory to use
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory(LabelFactory lf) {
    return new LabeledScoredTreeFactory(lf);
  }

  @Override
	public String nodeString() {
    StringBuilder buff = new StringBuilder();
    buff.append(super.nodeString());
    return buff.toString();
  }
}

