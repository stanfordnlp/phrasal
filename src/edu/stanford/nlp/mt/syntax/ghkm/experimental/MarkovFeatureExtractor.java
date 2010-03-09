package edu.stanford.nlp.mt.syntax.ghkm.experimental;

import edu.stanford.nlp.mt.syntax.ghkm.AbstractFeatureExtractor;
import edu.stanford.nlp.mt.syntax.ghkm.AlignmentTreeNode;
import edu.stanford.nlp.mt.syntax.ghkm.RuleInstance;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.CollinsHeadFinder;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;

/**
 * Tree Markovization for GHKM rules.
 *
 * @author Michel Galley
 */
public class MarkovFeatureExtractor extends AbstractFeatureExtractor {

  public static boolean headPos = Boolean.parseBoolean(System.getProperty("headPos", "false"));
  public static boolean headWord = Boolean.parseBoolean(System.getProperty("headWord", "false"));

  public static final String DEBUG_PROPERTY = "DebugMarkovFeatureExtractor";
  public static boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  boolean doTrain=true;

  double sumLogProb = 0.0;
  double normLogProb = 0.0;

  protected static final HeadFinder hf = new CollinsHeadFinder();

  enum ReorderingType {
     stayLeft, stayRight,
     moveLeft, moveRight,
     exitLeft, exitRight, other
  }

  final IntArrayBackoffEstimator
    hM = new IntArrayBackoffEstimator(10), // head
    lM = new IntArrayBackoffEstimator(10), // left modifiers
    rM = new IntArrayBackoffEstimator(10); // right modifiers

  @Override
	public void extractFeatures(RuleInstance rule) {
    AlignmentTreeNode n = rule.getExtractionNode();
    AlignmentTreeNode[] children = (AlignmentTreeNode[]) n.children();
    if(n.isPreTerminal())
      return;
    // Find head:
    AlignmentTreeNode h = (AlignmentTreeNode) hf.determineHead(n);
    if(h != null && !h.emptySpan()) {
      int hIdx = n.indexOf(h);
      int h1 = h.getLowFSpan(), h2 = h.getHighFSpan();
      for(int i=hIdx-1; i>=0; --i) {
        AlignmentTreeNode l = children[i];
        if(!l.emptySpan()) {
          int l1 = l.getLowFSpan(), l2 = l.getHighFSpan();
          ReorderingType type = ReorderingType.other;
          if(l2<h1) type = ReorderingType.stayLeft;
          else if(h2<l1) type = ReorderingType.moveRight;
          int[] cat = new int[] {type.ordinal()};
          int[] context = getContext(l,h);
          if(doTrain) {
            lM.addTrainingSample(cat,context);
          } else {
            double p = lM.probabilityOf(cat,context);
            String rStr = (rule != null) ? rule.toString() : "";
            System.err.printf("p=%f type=%s h=%s l=%s\trule=%s\n",
                p,type.toString(),h.label(),l.label(),rStr);
            sumLogProb += Math.log(p);
            ++normLogProb;
          }
        }
      }
      for(int i=hIdx+1; i<children.length; ++i) {
        AlignmentTreeNode r = children[i];
        if(!r.emptySpan()) {
          int r1 = h.getLowFSpan(), r2 = r.getHighFSpan();
          ReorderingType type = ReorderingType.other;
          if(r2<h1) type = ReorderingType.moveLeft;
          else if(h2<r1) type = ReorderingType.stayRight;
          int[] cat = new int[] {type.ordinal()};
          int[] context = getContext(r,h);
          if(doTrain) {
            rM.addTrainingSample(cat,context);
          } else {
            double p = rM.probabilityOf(cat,context);
            String rStr = (rule != null) ? rule.toString() : "";
            System.err.printf("p=%f type=%s h=%s r=%s\trule=%s\n",
                p,type.toString(),h.label(),r.label(),rStr);
            sumLogProb += Math.log(p);
            ++normLogProb;
          }
        }
      }
    }
  }

  public int[] getContext(Tree m, Tree h) {
    Tree hW = h.headTerminal(hf), hP = h.headPreTerminal(hf);
    Tree mW = m.headTerminal(hf), mP = m.headPreTerminal(hf);
    if(headWord && headPos) {
      return treeNodesToInt(new Tree[] {m,h,mP,hP,mW,hW});
    } else if(headPos) {
      return treeNodesToInt(new Tree[] {m,h,mP,hP});
    } else if(headWord) {
      return treeNodesToInt(new Tree[] {m,h,mW,hW});
    } else {
      return treeNodesToInt(new Tree[] {m,h});
    }
  }

  public void printStats() {
    System.err.printf("sum log prob=%g\n",sumLogProb);
    System.err.printf("norm log prob=%g\n",normLogProb);
    System.err.printf("cross entropy=%g\n",sumLogProb/normLogProb);
    System.err.printf("perplexity=%g\n",Math.exp(-sumLogProb/normLogProb));
  }

  private int[] treeNodesToInt(Tree[] nodes) {
    IString[] istr = new IString[nodes.length];
    for(int i=0; i<nodes.length; ++i)
      istr[i] = new IString(AlignmentTreeNode.getNodeString(nodes[i]));
    return IStrings.toIntArray(istr);
  }

  @Override
	public void save(String prefixName) {
  }
}
