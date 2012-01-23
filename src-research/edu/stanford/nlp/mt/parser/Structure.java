package edu.stanford.nlp.mt.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Structure used in shift-reduce dependency parser
 * 
 * @author heeyoung
 */
public class Structure {

  protected static final boolean useGoldTag = false;
  private static final boolean useLemma = false;

  protected LinkedStack<CoreLabel> stack;
  protected LinkedStack<CoreLabel> input;
  protected LinkedStack<TypedDependency> dependencies;
  protected LinkedStack<Action> actionTrace;
  protected HashMap<Integer, TreeGraphNode> inputIdxToNode;
  protected final TreeGraphNode root;
  protected HashSet<Integer> dependentsIdx;

  @SuppressWarnings("unchecked")
  @Override
  public Structure clone() {
    return new Structure(stack.clone(), input.clone(), dependencies.clone(), actionTrace.clone(), (HashMap<Integer, TreeGraphNode>)inputIdxToNode.clone(), (HashSet<Integer>) dependentsIdx.clone());
  }

  @Override
  public String toString() {
    StringBuilder sbuilder = new StringBuilder();
    sbuilder.append("Input:\n");
    sbuilder.append(input);
    sbuilder.append("Stack:\n");
    sbuilder.append(stack.toString());
    sbuilder.append("deps:\n");
    sbuilder.append(dependencies);
    return sbuilder.toString();
  }

  private Structure(LinkedStack<CoreLabel> stack, LinkedStack<CoreLabel> input, LinkedStack<TypedDependency> dependencies,
      LinkedStack<Action> actionTrace, HashMap<Integer, TreeGraphNode> inputIdxToNode, HashSet<Integer> dependentsIdx) {
    this.stack = stack;
    this.actionTrace = actionTrace;
    this.input = input;
    this.dependencies = dependencies;
    this.inputIdxToNode = inputIdxToNode;
    root = new TreeGraphNode(new CategoryWordTag("ROOT", Lexicon.BOUNDARY, Lexicon.BOUNDARY_TAG));
    root.label().set(TextAnnotation.class, "ROOT");
    this.dependentsIdx = dependentsIdx;
  }

  public Structure() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
    input = new LinkedStack<CoreLabel>();
    dependencies = new LinkedStack<TypedDependency>();
    this.inputIdxToNode = new HashMap<Integer, TreeGraphNode>();
    root = new TreeGraphNode(new CategoryWordTag("ROOT", Lexicon.BOUNDARY, Lexicon.BOUNDARY_TAG));
    root.label().set(TextAnnotation.class, "ROOT");
    dependentsIdx = new HashSet<Integer>();
  }

  public Structure(GrammaticalStructure gs, IncrementalTagger tagger, Morphology lemmatizer, POSTaggerAnnotator posTagger) {
    this();
    dependencies = new LinkedStack<TypedDependency>(gs.typedDependencies(true));
    input = new LinkedStack<CoreLabel>();
    int seqLen = tagger.ts.leftWindow() + 1;

    // to check the performance of POS tagger
    if(posTagger!=null) {
      List<CoreLabel> sent = new ArrayList<CoreLabel>();
      for (Tree treeNode : gs.root().getLeaves()) {
        TreeGraphNode node = (TreeGraphNode)treeNode;
        CoreLabel cl = node.label();
        cl.set(TextAnnotation.class, cl.get(ValueAnnotation.class));
        sent.add(cl);
      }
      posTagger.processText(sent);
    }

    for (Tree treeNode : gs.root().getLeaves()) {
      TreeGraphNode node = (TreeGraphNode)treeNode;
      CoreLabel cl = node.label();
      Tree p = treeNode.parent();
      cl.set(TextAnnotation.class, cl.get(ValueAnnotation.class));
      input.push(cl);
      inputIdxToNode.put(cl.get(IndexAnnotation.class), node);

      if(useGoldTag) cl.set(PartOfSpeechAnnotation.class, ((TreeGraphNode)p).label().get(ValueAnnotation.class));
      else {  // use incremental tagger
        int len = Math.min(seqLen, input.size());
        IString[] sequence = new IString[len];
        int i = sequence.length-1;
        Object[] toks = input.peekN(len);
        for(Object c : toks) {
          CoreLabel w = (CoreLabel) c;
          sequence[i--] = new IString(w.get(TextAnnotation.class));
        }
        tagger.tagWord(cl, sequence);
      }
      if(useLemma) lemmatizer.stem(cl);

    }

    // padding EOS token to guarantee every word has the right word
    CoreLabel cl = new CoreLabel();
    cl.set(TextAnnotation.class, TaggerConstants.EOS_WORD);
    cl.set(PartOfSpeechAnnotation.class, TaggerConstants.EOS_TAG);
    cl.set(IndexAnnotation.class, input.size()+1);
    input.push(cl);
  }

  public Structure(List<CoreLabel> sentence) {
    this();
    input = new LinkedStack<CoreLabel>();
    dependencies = new LinkedStack<TypedDependency>();
    int index = 0;
    for (CoreLabel l : sentence) {
      CoreLabel w = new CoreLabel(l);
      w.set(IndexAnnotation.class, index++);
      input.push(w);
    }
  }

  public LinkedStack<CoreLabel> getStack() {
    return stack;
  }

  public LinkedStack<CoreLabel> getInput() {
    return input;
  }

  public LinkedStack<TypedDependency> getDependencies() {
    return dependencies;
  }

  public LinkedStack<Action> getActionTrace() {
    return actionTrace;
  }

  public HashMap<Integer, TreeGraphNode> getInputIdxToNode() {
    return inputIdxToNode;
  }

  public void reset() {
    stack = new LinkedStack<CoreLabel>();
    actionTrace = new LinkedStack<Action>();
    dependencies = new LinkedStack<TypedDependency>();
    input = new LinkedStack<CoreLabel>();
    inputIdxToNode = new HashMap<Integer, TreeGraphNode>();
  }
  public void addRoot() {
    for(TreeGraphNode dep : findNonDependentNode(dependencies)) {
      TypedDependency dependency = new TypedDependency(GrammaticalRelation.ROOT, root, dep);
      dependencies.push(dependency);
    }
  }

  private Set<TreeGraphNode> findNonDependentNode(LinkedStack<TypedDependency> deps) {
    // make governor/dependent node set
    Set<TreeGraphNode> dependents = new HashSet<TreeGraphNode>();
    Set<TreeGraphNode> governors = new HashSet<TreeGraphNode>();
    for(TypedDependency dep : deps) {
      dependents.add(dep.dep());
      governors.add(dep.gov());
    }
    governors.removeAll(dependents);
    return governors;
  }
}
